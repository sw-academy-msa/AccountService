package com.msa.account.domain.seller.command.service.impl;
import com.msa.account.domain.seller.command.service.SellerBankEncryptionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
/** AES-GCM의 매 암호화마다 새로운 nonce를 사용하며 키·계좌번호를 로그에 넣지 않습니다. */
@Service
public class SellerBankEncryptionServiceImpl implements SellerBankEncryptionService {
    private final SecretKey key;
    private final SecureRandom random=new SecureRandom();
    public SellerBankEncryptionServiceImpl(@Qualifier("sellerDataKey") SecretKey key) { this.key=key; }
    @Override public String encrypt(String accountNumber) {
        try {
            byte[] nonce=new byte[12]; random.nextBytes(nonce);
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));
            byte[] encrypted=cipher.doFinal(accountNumber.getBytes(StandardCharsets.UTF_8));
            return "v1:"+Base64.getEncoder().encodeToString(nonce)+":"+Base64.getEncoder().encodeToString(encrypted);
        } catch(GeneralSecurityException exception) {
            throw new IllegalStateException("계좌 정보를 암호화할 수 없습니다.");
        }
    }
}
