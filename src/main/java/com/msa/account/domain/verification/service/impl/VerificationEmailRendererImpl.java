package com.msa.account.domain.verification.service.impl;

import com.msa.account.domain.verification.service.VerificationEmailRenderer;

import com.msa.account.domain.verification.dto.response.VerificationEmailContent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * STOCKDROP 흑백 디자인의 HTML 메일을 렌더링합니다.
 * 숫자 코드와 서버에서 포맷한 날짜만 치환하므로 사용자 입력이 HTML로 삽입되지 않습니다.
 */
@Component
public class VerificationEmailRendererImpl implements VerificationEmailRenderer {
    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter
            .ofPattern("yyyy.MM.dd HH:mm:ss", Locale.KOREAN)
            .withZone(ZoneId.of("Asia/Seoul"));
    private final String template;

    // 템플릿은 시작 시 한 번 읽으며 누락되면 발송 시점까지 숨기지 않고 즉시 실패합니다.
    public VerificationEmailRendererImpl() {
        try (var input = new ClassPathResource("templates/mail/email-verification.html").getInputStream()) {
            this.template = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("이메일 인증 HTML 템플릿을 읽을 수 없습니다.", exception);
        }
    }

    @Override
    // 앞자리 0을 보존하며 실제 서버 만료 시각을 한국 시간으로 표시합니다.
    public VerificationEmailContent render(String code, Instant expiresAt) {
        if (code == null || !code.matches("[0-9]{6}")) {
            throw new IllegalArgumentException("인증번호는 6자리 숫자여야 합니다.");
        }
        String expiry = EXPIRY_FORMAT.format(Objects.requireNonNull(expiresAt));
        String plainText = """
                STOCKDROP
                NEW FINDS. LIMITED TIME.

                이메일을 인증해 주세요.
                회원가입 화면에 아래 인증번호를 입력하면 다음 단계로 이동할 수 있어요.

                인증번호: %s
                유효 기한: %s (한국 시간)

                인증번호는 다른 사람에게 알려주지 마세요.
                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                이 메일은 회원가입 이메일 인증을 위해 자동 발송되었습니다.
                """.formatted(code, expiry);
        String html = template.replace("{{verificationCode}}", code).replace("{{expiresAt}}", expiry);
        return new VerificationEmailContent(plainText, html);
    }
}
