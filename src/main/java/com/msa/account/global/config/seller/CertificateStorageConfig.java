package com.msa.account.global.config.seller;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
/** 같은 S3 SDK로 로컬 에뮬레이터와 AWS를 사용하며 시작 시 네트워크 호출은 하지 않습니다. */
@Configuration
@EnableConfigurationProperties(CertificateStorageProperties.class)
public class CertificateStorageConfig {
    @Bean(destroyMethod="close")
    public S3Client sellerS3Client(CertificateStorageProperties p,Environment env) {
        var builder=S3Client.builder().region(Region.of(p.region()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(p.pathStyle()).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(3))
                        .socketTimeout(Duration.ofSeconds(5)))
                .overrideConfiguration(c->c.apiCallTimeout(Duration.ofSeconds(10)).apiCallAttemptTimeout(Duration.ofSeconds(5)));
        if(p.endpoint()!=null && !p.endpoint().isBlank()) {
            if(Arrays.asList(env.getActiveProfiles()).contains("prod"))
                throw new IllegalStateException("운영은 로컬 S3 endpoint를 사용할 수 없습니다.");
            builder.endpointOverride(URI.create(p.endpoint())).credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(p.accessKey(),p.secretKey())));
        } else builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
        return builder.build();
    }
}
