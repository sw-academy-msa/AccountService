package com.msa.account.domain.verification.command.service.impl;

import com.msa.account.domain.verification.command.service.VerificationEmailRenderer;

import com.msa.account.domain.verification.config.EmailVerificationProperties;
import com.msa.account.domain.verification.exception.EmailVerificationErrorCode;
import com.msa.account.domain.verification.exception.EmailVerificationException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 외부 메일을 보내지 않고 실제 MIME 구조·한글·일반 텍스트 대체 본문을 검증합니다. */
class VerificationMailSenderImplTest {
    private final VerificationEmailRenderer renderer = new VerificationEmailRendererImpl();
    private final Instant expiresAt = Instant.parse("2026-10-23T15:05:00Z");
    private final EmailVerificationProperties properties = new EmailVerificationProperties(
            Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofSeconds(60), 5, 10,
            "no-reply@example.com");

    @Test
    // 제목·발신자·수신자 및 두 가지 본문이 UTF-8로 만들어지고 앞자리 0도 보존되는지 확인합니다.
    void buildsAlternativeHtmlAndPlainTextMessage() throws Exception {
        JavaMailSender smtp = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(smtp.createMimeMessage()).thenReturn(message);

        new VerificationMailSenderImpl(smtp, properties, renderer)
                .send("buyer@example.com", "001234", expiresAt);
        message.saveChanges();

        verify(smtp).send(message);
        assertThat(message.getSubject()).isEqualTo("[STOCKDROP] 회원가입 이메일 인증번호");
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("STOCKDROP");
        assertThat(((InternetAddress) message.getAllRecipients()[0]).getAddress()).isEqualTo("buyer@example.com");
        assertThat(message.isMimeType("multipart/alternative")).isTrue();
        MimeMultipart parts = (MimeMultipart) message.getContent();
        assertThat(parts.getCount()).isEqualTo(2);
        assertThat(parts.getBodyPart(0).isMimeType("text/plain")).isTrue();
        assertThat(parts.getBodyPart(1).isMimeType("text/html")).isTrue();
        assertThat(parts.getBodyPart(1).getContentType()).containsIgnoringCase("charset=UTF-8");
        assertThat(parts.getBodyPart(0).getContent().toString()).contains("001234", "한국 시간");
        assertThat(parts.getBodyPart(1).getContent().toString())
                .contains("001234", "2026.10.24 00:05:00", "이메일을 인증해 주세요.")
                .doesNotContain("{{verificationCode}}", "{{expiresAt}}", "<script", "<img");
    }

    @Test
    // 잘못된 템플릿 입력을 거절하여 인증번호 위치에 임의 HTML을 삽입할 수 없게 합니다.
    void rejectsNonNumericVerificationCode() {
        assertThatIllegalArgumentException().isThrownBy(() -> renderer.render("<img>", expiresAt));
        assertThatIllegalArgumentException().isThrownBy(() -> renderer.render(null, expiresAt));
    }

    @Test
    // MIME로 바꿔도 SMTP 오류는 기존 도메인 오류로 변환하여 발급 트랜잭션이 롤백되게 합니다.
    void convertsSmtpFailureToDomainException() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(smtp.createMimeMessage()).thenReturn(message);
        doThrow(new MailSendException("test SMTP failure")).when(smtp).send(message);

        assertThatThrownBy(() -> new VerificationMailSenderImpl(smtp, properties, renderer)
                .send("buyer@example.com", "001234", expiresAt))
                .isInstanceOfSatisfying(EmailVerificationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EmailVerificationErrorCode.DELIVERY_FAILED));
    }

    @Test
    // 가짜 코드로 브라우저 미리보기 파일을 생성합니다. 실제 수신자·인증 데이터는 포함하지 않습니다.
    void createsSafeHtmlPreview() throws Exception {
        var preview = renderer.render("001234", expiresAt);
        Path output = Path.of("build/reports/mail/email-verification-preview.html");
        Files.createDirectories(output.getParent());
        Files.writeString(output, preview.html(), StandardCharsets.UTF_8);
        assertThat(preview.html()).contains("STOCKDROP", "001234");
    }
}
