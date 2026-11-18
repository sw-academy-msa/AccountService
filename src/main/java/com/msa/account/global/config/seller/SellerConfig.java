package com.msa.account.global.config.seller;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import static java.nio.file.StandardOpenOption.*;
/** 로컬 암호화 키는 재시작에도 유지하고 운영 A/B에는 같은 외부 비밀 키를 주입합니다. */
@Configuration
@EnableConfigurationProperties(SellerProperties.class)
public class SellerConfig {
    @Bean("sellerDataKey")
    public SecretKey sellerDataKey(SellerProperties properties, Environment env) throws Exception {
        byte[] bytes;
        var profiles=Arrays.asList(env.getActiveProfiles());
        if(!properties.dataEncryptionKey().isBlank()) {
            try { bytes=Base64.getDecoder().decode(properties.dataEncryptionKey()); }
            catch(IllegalArgumentException exception) { throw new IllegalStateException("판매자 암호화 키 형식을 확인해 주세요."); }
        } else if(profiles.contains("test")) bytes=generate();
        else if(profiles.contains("local") && !profiles.contains("prod")) {
            var folder=Path.of(".keys"); Files.createDirectories(folder);
            try(var channel=FileChannel.open(folder.resolve("seller-data.lock"),CREATE,WRITE); var ignored=channel.lock()) {
                var file=folder.resolve("seller-data.key");
                if(!Files.exists(file)) Files.write(file,generate(),CREATE_NEW);
                bytes=Files.readAllBytes(file);
            }
        } else throw new IllegalStateException("SELLER_DATA_ENCRYPTION_KEY를 지정해야 합니다.");
        if(bytes.length!=32) throw new IllegalStateException("판매자 암호화 키는 32바이트여야 합니다.");
        return new SecretKeySpec(bytes,"AES");
    }
    private byte[] generate() { byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes); return bytes; }
}
