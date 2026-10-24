package com.msa.account.global.config.security;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import static java.nio.file.StandardOpenOption.*;

/** Account만 개인키를 보유합니다. 다른 서비스에는 JWKS로 공개키만 제공합니다. */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class JwtConfig {
    @Bean
    // 테스트는 독립 키, 로컬은 재시작에도 유지되는 파일, 운영은 외부 비밀 파일을 사용합니다.
    public RSAKey accountSigningKey(AuthProperties properties, Environment environment, ResourceLoader loader) throws Exception {
        RSAKey key;
        if (properties.signingKeyLocation() != null && !properties.signingKeyLocation().isBlank()) {
            try (var stream = loader.getResource(properties.signingKeyLocation()).getInputStream()) {
                key = RSAKey.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        } else if (Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            key = generate();
        } else if (Arrays.asList(environment.getActiveProfiles()).contains("local")
                && !Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            Path folder = Path.of(".keys");
            Files.createDirectories(folder);
            // 파일 잠금으로 로컬 앱을 동시에 켜더라도 서로 다른 키를 생성하지 않게 합니다.
            try (var channel = FileChannel.open(folder.resolve("account-signing.lock"), CREATE, WRITE);
                 var ignored = channel.lock()) {
                Path path = folder.resolve("account-signing.jwk");
                if (!Files.exists(path)) Files.writeString(path, generate().toJSONString(), StandardCharsets.UTF_8, CREATE_NEW);
                key = RSAKey.parse(Files.readString(path, StandardCharsets.UTF_8));
            }
        } else {
            throw new IllegalStateException("JWT_SIGNING_KEY_LOCATION을 지정해야 합니다.");
        }
        if (!key.isPrivate() || key.getKeyID() == null || key.toRSAPublicKey().getModulus().bitLength() < 2048)
            throw new IllegalStateException("kid가 있는 2048비트 이상의 RSA 개인키가 필요합니다.");
        return key;
    }

    // kid는 JWKS에서 검증에 사용할 공개키를 선택하는 식별자이며 비밀값이 아닙니다.
    private RSAKey generate() throws Exception {
        return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey key) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    // 서명뿐 아니라 issuer·audience·유효 시간을 모두 확인합니다.
    public JwtDecoder jwtDecoder(RSAKey key, AuthProperties properties, Clock clock) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        JwtTimestampValidator time = new JwtTimestampValidator(Duration.ofSeconds(30));
        time.setClock(clock);
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains(properties.audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "잘못된 audience입니다.", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(time,
                new JwtIssuerValidator(properties.issuer()), audience,
                new JwtClaimValidator<java.time.Instant>("exp", Objects::nonNull),
                new JwtClaimValidator<String>("token_use", "access"::equals)));
        return decoder;
    }
}
