#!/usr/bin/env python3
"""Build/sign the demo APK, upload it to COS and register an update draft."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from urllib.error import HTTPError
from urllib.parse import quote, urlsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler

ROOT = Path(__file__).resolve().parents[2]
APP_ID = 'com.xpg.wifidemo'
KEYS = ('APP_UPDATE_BASE_URL', 'APP_UPDATE_ALLOW_HTTP', 'SDK_LOG_API_URL',
        'MOBILE_RELEASE_TOKEN', 'ANDROID_KEYSTORE', 'ANDROID_KEY_ALIAS',
        'ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_PASSWORD', 'ANDROID_SDK_ROOT',
        'COS_SECRET_ID', 'COS_SECRET_KEY', 'COS_BUCKET_NAME', 'APP_ID',
        'COS_REGION', 'COS_PREFIX', 'COS_SESSION_TOKEN')


def config():
    values = {}
    env_file = Path(__file__).with_name('.env')
    if env_file.exists():
        for line in env_file.read_text(encoding='utf-8-sig').splitlines():
            line = line.strip().removeprefix('export ')
            if line and not line.startswith('#') and '=' in line:
                key, value = line.split('=', 1)
                if key.strip() in KEYS:
                    values[key.strip()] = value.strip().strip('\"\'')
    values.update({key: os.environ[key] for key in KEYS if key in os.environ})
    return values


def require(values, *keys):
    missing = [key for key in keys if not values.get(key)]
    if missing:
        raise ValueError('缺少配置: ' + ', '.join(missing))


def https(address, allow_http=False):
    value = urlsplit(address)
    if value.scheme not in (('https', 'http') if allow_http else ('https',)) or not value.hostname or value.username or value.password or value.fragment or value.query:
        raise ValueError('需要完整 HTTPS 地址（不能含认证、查询参数或片段）')
    return address.rstrip('/')


def digest(path):
    result = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()


def version(value):
    if not re.fullmatch(r'(0|[1-9][0-9]{0,8})(\.(0|[1-9][0-9]{0,8})){1,2}', value):
        raise ValueError('版本号格式应为 1.2 或 1.2.3')
    return value


def build_tools(values):
    sdk = values.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
    if not sdk:
        local = ROOT / 'local.properties'
        if local.exists():
            sdk = next((s.split('=', 1)[1].strip() for s in local.read_text().splitlines() if s.startswith('sdk.dir=')), None)
    if not sdk:
        raise ValueError('请配置 ANDROID_SDK_ROOT')
    candidates = [p for p in (Path(sdk) / 'build-tools').iterdir() if re.fullmatch(r'\d+\.\d+\.\d+', p.name)]
    if not candidates:
        raise ValueError('没有找到 Android build-tools')
    return max(candidates, key=lambda p: tuple(int(n) for n in p.name.split('.')))


def apk_metadata(path, tools):
    output = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(path)], text=True)
    match = re.search(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", output)
    if not match:
        raise ValueError('无法读取 APK 包名和版本')
    subprocess.run([str(tools / 'apksigner'), 'verify', '--verbose', str(path)], check=True, stdout=subprocess.DEVNULL)
    return match.group(1), int(match.group(2)), match.group(3)


def build(args, values):
    version(args.version)
    if not 1 <= args.build <= 2100000000 or not 0 <= args.min_build <= args.build:
        raise ValueError('构建号或最低构建号无效')
    require(values, 'APP_UPDATE_BASE_URL', 'ANDROID_KEYSTORE', 'ANDROID_KEY_ALIAS', 'ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_PASSWORD')
    base = https(values['APP_UPDATE_BASE_URL'], values.get('APP_UPDATE_ALLOW_HTTP') == 'true')
    tools = build_tools(values)
    keystore = Path(values['ANDROID_KEYSTORE']).expanduser().resolve(strict=True)
    directory = Path(args.output_directory).resolve() if args.output_directory else ROOT / 'release' / 'mobile' / f'{args.version}-{args.build}' / args.channel
    if directory.exists():
        raise ValueError(f'输出目录已存在，拒绝覆盖: {directory}')
    notes = Path(args.notes_file).read_text(encoding='utf-8') if args.notes_file else ''
    if len(notes.encode()) > 32000:
        raise ValueError('更新说明过长')
    env = {**os.environ, **values, 'APP_UPDATE_BASE_URL': base, 'APP_UPDATE_CHANNEL': args.channel}
    command = [str(ROOT / 'gradlew'), ':app:assembleRelease', '-PskipLocalReleaseSigning=true', '-Pkotlin.compiler.execution.strategy=in-process', f'-PappVersionName={args.version}', f'-PappVersionCode={args.build}']
    if args.offline:
        command.append('--offline')
    subprocess.run(command, cwd=ROOT, env=env, check=True)
    unsigned = ROOT / 'app/build/outputs/apk/release/app-release-unsigned.apk'
    if not unsigned.exists():
        raise ValueError('未找到 app-release-unsigned.apk')
    directory.mkdir(parents=True)
    aligned, apk = directory / 'aligned.apk', directory / 'app.apk'
    try:
        subprocess.run([str(tools / 'zipalign'), '-P', '16', '-f', '4', str(unsigned), str(aligned)], check=True)
        subprocess.run([str(tools / 'apksigner'), 'sign', '--ks', str(keystore), '--ks-key-alias', values['ANDROID_KEY_ALIAS'],
                        '--ks-pass', 'env:ANDROID_KEYSTORE_PASSWORD', '--key-pass', 'env:ANDROID_KEY_PASSWORD',
                        '--out', str(apk), str(aligned)], env=env, check=True)
        if apk_metadata(apk, tools) != (APP_ID, args.build, args.version):
            raise ValueError('签名后 APK 包名或版本不匹配')
        manifest = dict(app_id=APP_ID, platform='android', channel=args.channel, version=args.version,
                        build=args.build, min_build=args.min_build, min_version='', notes=notes,
                        size=apk.stat().st_size, sha256=digest(apk), url='')
        (directory / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    finally:
        aligned.unlink(missing_ok=True)
    print(f'已生成签名 APK 和 manifest: {directory}')
    return directory


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Release credentials must never follow redirects to another endpoint.
        return None


OPENER = build_opener(NoRedirect)


def api(values, path, payload, method='POST'):
    require(values, 'MOBILE_RELEASE_TOKEN')
    if len(values['MOBILE_RELEASE_TOKEN']) < 32:
        raise ValueError('MOBILE_RELEASE_TOKEN 至少 32 字符')
    base = https(values.get('SDK_LOG_API_URL') or values.get('APP_UPDATE_BASE_URL', ''), values.get('APP_UPDATE_ALLOW_HTTP') == 'true')
    req = Request(base + path, json.dumps(payload).encode(), method=method,
                  headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + values['MOBILE_RELEASE_TOKEN']})
    try:
        with OPENER.open(req, timeout=30) as response:
            result = json.load(response)
    except HTTPError as error:
        try:
            detail = json.loads(error.read(65536)).get('errorMessage', '服务请求失败')
        except (ValueError, AttributeError):
            detail = '服务请求失败'
        raise ValueError(f'后台返回 {error.code}: {detail}') from None
    if not result.get('success'):
        raise ValueError('后台操作失败')
    return result


def read_manifest(directory):
    directory = Path(directory).resolve()
    manifest = json.loads((directory / 'manifest.json').read_text())
    apk = directory / 'app.apk'
    if apk.is_symlink() or not apk.is_file():
        raise ValueError('目录内必须存在真实 app.apk')
    version(manifest['version'])
    if manifest.get('app_id') != APP_ID or manifest.get('platform') != 'android' or manifest.get('channel') not in ('stable', 'beta'):
        raise ValueError('manifest 应用或渠道无效')
    if not isinstance(manifest.get('build'), int) or not 1 <= manifest['build'] <= 2100000000:
        raise ValueError('manifest 构建号无效')
    if apk.stat().st_size != manifest['size'] or digest(apk) != manifest['sha256']:
        raise ValueError('APK 与 manifest 校验不一致')
    return apk, manifest


def public_verify(address, manifest):
    https(address)
    total, result = 0, hashlib.sha256()
    with OPENER.open(Request(address), timeout=60) as response:
        for block in iter(lambda: response.read(1024 * 1024), b''):
            total += len(block)
            if total > manifest['size']:
                raise ValueError('公开下载的 APK 大小不匹配')
            result.update(block)
    if total != manifest['size'] or result.hexdigest() != manifest['sha256']:
        raise ValueError('公开下载校验失败，不能登记发布')


def upload(args, values):
    apk, manifest = read_manifest(args.directory)
    if apk_metadata(apk, build_tools(values)) != (APP_ID, manifest['build'], manifest['version']):
        raise ValueError('APK 元数据与 manifest 不匹配')
    require(values, 'COS_SECRET_ID', 'COS_SECRET_KEY', 'COS_BUCKET_NAME', 'APP_ID')
    import boto3
    from botocore.config import Config
    region = values.get('COS_REGION', 'ap-guangzhou')
    bucket = values['COS_BUCKET_NAME']
    if not bucket.endswith('-' + values['APP_ID']):
        bucket += '-' + values['APP_ID']
    prefix = values.get('COS_PREFIX', 'tools/mobile-apps').strip('/')
    if not prefix or any(part in ('', '.', '..') for part in prefix.split('/')):
        raise ValueError('COS_PREFIX 无效')
    # Content-addressed objects cannot overwrite a previous release's APK.
    key = f"{prefix}/{APP_ID}/{manifest['channel']}/{manifest['build']}/{manifest['sha256']}/app.apk"
    client = boto3.client('s3', aws_access_key_id=values['COS_SECRET_ID'], aws_secret_access_key=values['COS_SECRET_KEY'],
                          aws_session_token=values.get('COS_SESSION_TOKEN') or None, region_name=region,
                          endpoint_url=f'https://cos.{region}.myqcloud.com',
                          config=Config(signature_version='s3v4', request_checksum_calculation='when_required', response_checksum_validation='when_required'))
    existing = None
    try:
        existing = client.head_object(Bucket=bucket, Key=key)
    except Exception as error:
        if getattr(error, 'response', {}).get('Error', {}).get('Code') not in ('404', 'NotFound', 'NoSuchKey'):
            raise
    if existing:
        if existing.get('ContentLength') != manifest['size'] or existing.get('Metadata', {}).get('sha256') != manifest['sha256']:
            raise ValueError('COS 已有对象校验不匹配，拒绝覆盖')
    else:
        with apk.open('rb') as stream:
            client.put_object(Bucket=bucket, Key=key, Body=stream, ContentLength=manifest['size'],
                              ContentType='application/vnd.android.package-archive', Metadata={'sha256': manifest['sha256']})
    manifest['url'] = f'https://{bucket}.cos.{region}.myqcloud.com/{quote(key, safe="/")}'
    public_verify(manifest['url'], manifest)
    (apk.parent / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    print('上传并完成公开下载校验: ' + manifest['url'])
    if args.register:
        register(args, values)


def register(args, values):
    _, manifest = read_manifest(args.directory)
    public_verify(manifest['url'], manifest)
    result = api(values, '/mobile/releases', manifest)
    print(f"后台版本 ID: {result['data']['id']}；状态: {'已发布' if result['data']['published'] else '草稿'}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    p = sub.add_parser('build', help='构建并签名 APK，生成 manifest')
    p.add_argument('--version', required=True)
    p.add_argument('--build', type=int, required=True)
    p.add_argument('--channel', choices=['stable', 'beta'], default='stable')
    p.add_argument('--min-build', type=int, default=0)
    p.add_argument('--notes-file')
    p.add_argument('--output-directory', help='可选：指定全新的产物目录')
    p.add_argument('--offline', action='store_true', help='仅使用已缓存的 Gradle 依赖')
    p = sub.add_parser('upload', help='上传 COS 并核验公开下载')
    p.add_argument('directory')
    p.add_argument('--register', action='store_true', help='上传成功后登记草稿')
    p = sub.add_parser('register', help='将已上传安装包登记为后台草稿')
    p.add_argument('directory')
    p = sub.add_parser('publish', help='发布已登记版本')
    p.add_argument('id', type=int)
    p = sub.add_parser('withdraw', help='下架已登记版本')
    p.add_argument('id', type=int)
    args = parser.parse_args()
    values = config()
    if args.command in ('publish', 'withdraw'):
        if args.id <= 0:
            raise ValueError('版本 ID 无效')
        api(values, f'/mobile/releases/{args.id}/state', {'published': args.command == 'publish'}, 'PATCH')
        print('版本已发布' if args.command == 'publish' else '版本已下架')
    else:
        globals()[args.command](args, values)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print(f'发布失败: {error}', file=sys.stderr)
        sys.exit(1)
