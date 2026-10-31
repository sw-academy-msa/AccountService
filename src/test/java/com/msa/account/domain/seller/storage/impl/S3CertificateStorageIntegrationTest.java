package com.msa.account.domain.seller.storage.impl;
import com.msa.account.domain.seller.command.dto.response.CertificateContent;
import com.msa.account.domain.seller.exception.SellerException;
import com.msa.account.global.config.seller.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
/** 실제 Docker S3 API와 AWS SDK를 연결해 저장·조회·삭제를 검증합니다. 실제 AWS 키는 쓰지 않습니다. */
@Testcontainers
class S3CertificateStorageIntegrationTest {
    @Container
    static final GenericContainer<?> S3=new GenericContainer<>("adobe/s3mock:5.2.3")
            .withExposedPorts(9090).waitingFor(Wait.forHttp("/favicon.ico").forPort(9090))
            .withStartupTimeout(Duration.ofSeconds(90));
    @Test
    void storesPdfOutsideDatabaseAndReadsSameBytes() {
        var properties=new CertificateStorageProperties("seller-test","ap-northeast-2",
                "http://"+S3.getHost()+":"+S3.getMappedPort(9090),true,true,"test","test-secret");
        try(var client=new CertificateStorageConfig().sellerS3Client(properties,new MockEnvironment())) {
            var storage=new S3CertificateStorageImpl(client,properties);
            byte[] pdf="%PDF-1.7\nlocal-test\n%%EOF".getBytes(StandardCharsets.US_ASCII);
            String key="seller-certificates/test/business.pdf";
            storage.put(key,new CertificateContent("business.pdf",pdf));
            assertThat(storage.get(key)).isEqualTo(pdf);
            storage.delete(key);
            assertThatThrownBy(()->storage.get(key)).isInstanceOf(SellerException.class);
        }
    }
}
