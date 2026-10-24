package com.msa.account.domain.verification.command.service.impl;

import com.msa.account.domain.verification.command.service.VerificationMailSender;
import com.msa.account.domain.verification.command.service.VerificationEmailRenderer;

import com.msa.account.global.config.mail.EmailVerificationProperties;
import com.msa.account.domain.verification.exception.EmailVerificationErrorCode;
import com.msa.account.domain.verification.exception.EmailVerificationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import java.time.Instant;
import com.msa.account.domain.verification.enums.VerificationPurpose;

/** SMTP 발송 구현입니다. 로컬은 Mailpit, 운영은 환경변수에 지정한 SMTP를 사용합니다. */
@Component
public class VerificationMailSenderImpl implements VerificationMailSender {
    private final JavaMailSender mailSender;
    private final EmailVerificationProperties properties;
    private final VerificationEmailRenderer renderer;

    public VerificationMailSenderImpl(JavaMailSender mailSender, EmailVerificationProperties properties,
            VerificationEmailRenderer renderer) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.renderer = renderer;
    }

    // 인증번호는 수신 메일에만 포함하고 서버 로그나 API 응답으로 노출하지 않습니다.
    public void send(String email, String code, Instant expiresAt, VerificationPurpose purpose) {
        try {
            var content = renderer.render(code, expiresAt, purpose);
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from(), "STOCKDROP");
            helper.setTo(email);
            helper.setSubject(purpose.subject());
            // 첨부파일 없는 메일이므로 최상위 본문을 alternative로 직접 구성합니다.
            // 일반 텍스트를 먼저 두고 HTML을 뒤에 두면 수신 앱이 지원하는 표현을 선택합니다.
            var plainPart = new MimeBodyPart();
            plainPart.setText(content.plainText(), StandardCharsets.UTF_8.name());
            var htmlPart = new MimeBodyPart();
            htmlPart.setText(content.html(), StandardCharsets.UTF_8.name(), "html");
            var alternatives = new MimeMultipart("alternative");
            alternatives.addBodyPart(plainPart);
            alternatives.addBodyPart(htmlPart);
            message.setContent(alternatives);
            mailSender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException exception) {
            throw new EmailVerificationException(EmailVerificationErrorCode.DELIVERY_FAILED);
        }
    }
}
