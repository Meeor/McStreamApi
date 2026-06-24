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

fun soopPairingFallbackHtmlPage(code: String): String {
    val safeCode = code.escapeHtml()
    return """
    <!doctype html>
    <html lang="ko">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <title>McStreamApi 인증 확인</title>
      <style>
        body { font-family: system-ui, sans-serif; margin: 0; min-height: 100vh; display: grid; place-items: center; background: #f6f7f9; color: #16181d; }
        main { width: min(520px, calc(100vw - 32px)); background: white; border: 1px solid #dadde3; border-radius: 8px; padding: 28px; }
        h1 { font-size: 24px; margin: 0 0 12px; }
        p { line-height: 1.6; margin: 0 0 16px; color: #4b5563; }
        label { display: block; margin: 0 0 8px; font-weight: 700; }
        input { box-sizing: border-box; width: 100%; font: inherit; padding: 12px; border: 1px solid #c8ced8; border-radius: 6px; text-transform: uppercase; }
        button { margin-top: 14px; width: 100%; font: inherit; font-weight: 700; padding: 12px; border: 0; border-radius: 6px; background: #1f6feb; color: white; cursor: pointer; }
      </style>
    </head>
    <body>
      <main>
        <h1>SOOP 자동 인증을 완료할 수 없습니다.</h1>
        <p>Minecraft 서버에서 안내된 인증 코드를 입력해주세요.</p>
        <form method="post" action="">
          <input type="hidden" name="code" value="$safeCode">
          <label for="pairingCode">인증 코드</label>
          <input id="pairingCode" name="pairingCode" autocomplete="one-time-code" required maxlength="16">
          <button type="submit">인증 완료</button>
        </form>
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
