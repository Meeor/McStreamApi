package kr.meeor.mcstreamapi.authserver.route

fun successHtmlPage(): String =
    """
    <!doctype html>
    <html lang="ko">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <title>McStreamApi 인증 완료</title>
      <style>
        body { font-family: system-ui, sans-serif; margin: 0; min-height: 100vh; display: grid; place-items: center; background: #f6f7f9; color: #16181d; }
        main { width: min(520px, calc(100vw - 32px)); background: white; border: 1px solid #dadde3; border-radius: 8px; padding: 28px; }
        h1 { font-size: 24px; margin: 0 0 12px; }
        p { line-height: 1.6; margin: 0; color: #4b5563; }
      </style>
    </head>
    <body>
      <main>
        <h1>인증이 완료되었습니다.</h1>
        <p>Minecraft 서버로 돌아가 연결 상태를 확인해주세요.</p>
      </main>
    </body>
    </html>
    """.trimIndent()

fun failureHtmlPage(error: String, message: String): String {
    val safeError = error.escapeHtml()
    val safeMessage = message.escapeHtml()
    return """
    <!doctype html>
    <html lang="ko">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <title>McStreamApi 인증 실패</title>
      <style>
        body { font-family: system-ui, sans-serif; margin: 0; min-height: 100vh; display: grid; place-items: center; background: #f6f7f9; color: #16181d; }
        main { width: min(520px, calc(100vw - 32px)); background: white; border: 1px solid #dadde3; border-radius: 8px; padding: 28px; }
        h1 { font-size: 24px; margin: 0 0 12px; }
        code { display: inline-block; margin: 0 0 12px; color: #b42318; }
        p { line-height: 1.6; margin: 0; color: #4b5563; }
      </style>
    </head>
    <body>
      <main>
        <h1>인증에 실패했습니다.</h1>
        <code>$safeError</code>
        <p>$safeMessage Minecraft 서버에서 다시 시도해주세요.</p>
      </main>
    </body>
    </html>
    """.trimIndent()
}

private fun String.escapeHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
