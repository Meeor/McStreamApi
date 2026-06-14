# Release

## Build

Snapshot build:

```bash
./gradlew build releaseBundle
```

Release version build:

```bash
./gradlew build releaseBundle -PreleaseVersion=0.1.0
```

Windows:

```powershell
.\gradlew.bat build releaseBundle --no-daemon --console=plain '-PreleaseVersion=0.1.0'
```

## Artifacts

```text
plugin/build/libs/McStreamApi-<version>.jar
auth-server/build/libs/McStreamApi-AuthServer-<version>.jar
build/release/McStreamApi-<version>-release.zip
```

The release zip contains:

- Plugin jar
- AuthServer jar
- Example config files
- `ASstart.sh`
- `ASstop.sh`
- `README.md`
- Public docs

The release zip must not contain real `config.yml`, token files, logs, or private deployment notes.

## GitHub Release Draft

Title:

```text
McStreamApi 0.1.0
```

Body:

```text
## 포함

- McStreamApi Paper/Spigot plugin jar
- McStreamApi AuthServer jar
- example config files
- operation scripts
- public documentation

## 상태

- Chzzk OAuth 연결 구현
- Chzzk 후원 세션 구조 구현
- SOOP OAuth 구현
- SOOP 후원 이벤트 세션은 공식 API 확인 전까지 보류

## 요구사항

- Java 21
- Paper/Spigot 1.21.x
- HTTPS reverse proxy for AuthServer

## 주의

실제 config.yml, clientSecret, sharedSecret, token 파일은 릴리즈에 포함하지 않습니다.
```
