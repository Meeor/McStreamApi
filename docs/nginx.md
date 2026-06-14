# Nginx Reverse Proxy

AuthServer를 `/mca/` 경로 뒤에 붙이는 예시입니다.

```nginx
location = /mca {
    return 301 /mca/;
}

location /mca/ {
    proxy_pass http://127.0.0.1:18080/;
    proxy_http_version 1.1;

    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;

    proxy_connect_timeout 10s;
    proxy_send_timeout 30s;
    proxy_read_timeout 30s;
}
```

외부 URL:

```text
https://auth.example.com/mca/oauth/chzzk/callback
```

AuthServer 내부 route:

```text
/oauth/chzzk/callback
```

`proxy_pass http://127.0.0.1:18080/;` 끝의 `/`를 빼면 경로 전달이 달라지므로 유지합니다.

적용:

```bash
sudo nginx -t
sudo systemctl reload nginx
```
