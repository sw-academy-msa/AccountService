package com.msa.account.domain.account.command.service.impl;

import com.msa.account.domain.account.client.WithdrawalClient;
import com.msa.account.domain.account.command.dto.response.WithdrawalLeaseResponse;
import com.msa.account.domain.account.command.service.*;
import com.msa.account.domain.account.exception.*;
import com.msa.account.domain.account.query.dto.response.WithdrawalEligibilityResponse;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;

import java.util.*;

/** Order/Product 차단을 먼저 확정하고 원본을 확인합니다. 실패 시 해제 의도를 영속화해 재시작 후 복구합니다. */
@Service
public class WithdrawalEligibilityServiceImpl implements WithdrawalEligibilityService {
    private final WithdrawalClient client;
    private final WithdrawalOperationService operations;

    public WithdrawalEligibilityServiceImpl(
            WithdrawalClient client, WithdrawalOperationService operations) {
        this.client = client;
        this.operations = operations;
    }

    public WithdrawalEligibilityResponse inspect(UUID account) {
        if (!client.enabled()) return WithdrawalEligibilityResponse.unavailable();
        try {
            return inspect(account, null);
        } catch (RuntimeException ex) {
            return WithdrawalEligibilityResponse.unavailable();
        }
    }

    public WithdrawalEligibilityResponse acquireClearance(UUID account) {
        if (!client.enabled()) return WithdrawalEligibilityResponse.unavailable();
        var lease = operations.begin(account);
        if (lease.isEmpty()) return WithdrawalEligibilityResponse.unavailable();
        try {
            var response = inspect(account, lease.get());
            if (!response.canWithdraw()) {
                abort(lease.get(), false);
                return response;
            }
            operations.clear(lease.get());
            return response;
        } catch (RuntimeException ex) {
            abort(lease.get(), false);
            return WithdrawalEligibilityResponse.unavailable();
        }
    }

    public void commitClearance(UUID account, UUID operation) {
        operations.commit(account, operation);
    }

    public void releaseClearance(UUID account, UUID operation) {
        if (operation != null) abort(new WithdrawalLeaseResponse(account, operation), false);
    }

    public void recover() {
        for (var lease : operations.due()) abort(lease, true);
    }

    private WithdrawalEligibilityResponse inspect(UUID account, WithdrawalLeaseResponse lease) {
        String path = "/internal/v1/account-withdrawals/" + account;
        String method = lease == null ? "GET" : "PUT";
        String target = lease == null ? path : path + "/fences/" + lease.operationId();
        var order = client.exchange("order-command-service", method, target, null);
        renew(lease);
        var product = client.exchange("product-command-service", method, target, null);
        renew(lease);
        long active = count(order, "activeOrders"),
                sales =
                        Math.addExact(
                                count(product, "activeSales"),
                                count(product, "activeReservations")),
                pending = count(order, "pendingSettlements");
        // 진행 거래가 있으면 안전한 거절로 끝내고 대량 과거 결제를 불필요하게 조회하지 않습니다.
        if (active == 0 && sales == 0 && pending == 0) {
            int page = 0;
            boolean next;
            do {
                var payments =
                        client.exchange(
                                "order-command-service",
                                "GET",
                                path + "/payments?page=" + page,
                                null);
                renew(lease);
                if (!payments.path("references").isArray()
                        || !payments.path("hasNext").isBoolean()
                        || payments.path("references").size() > 100) throw unavailable();
                if (payments.path("references").size() > 0) {
                    var result =
                            client.exchange(
                                    "payment-service",
                                    "POST",
                                    "/internal/v1/account-withdrawals/payments",
                                    Map.of("references", payments.path("references")));
                    renew(lease);
                    pending = Math.addExact(pending, count(result, "pendingPayments"));
                }
                next = payments.path("hasNext").asBoolean();
                if (next && payments.path("references").size() == 0) throw unavailable();
                page = Math.incrementExact(page);
            } while (next && pending == 0);
        }
        return WithdrawalEligibilityResponse.builder()
                .verified(true)
                .canWithdraw(active == 0 && sales == 0 && pending == 0)
                .activeOrders(active)
                .activeSales(sales)
                .pendingSettlements(pending)
                .operationId(lease == null ? null : lease.operationId())
                .build();
    }

    private void renew(WithdrawalLeaseResponse lease) {
        if (lease != null) operations.renew(lease);
    }

    private void abort(WithdrawalLeaseResponse lease, boolean expiredOnly) {
        if (!operations.abort(lease, expiredOnly)) return;
        boolean released = true;
        for (String service : List.of("order-command-service", "product-command-service"))
            try {
                client.exchange(
                        service,
                        "DELETE",
                        "/internal/v1/account-withdrawals/"
                                + lease.accountId()
                                + "/fences/"
                                + lease.operationId(),
                        null);
            } catch (RuntimeException ex) {
                released = false;
            }
        if (released) operations.released(lease);
    }

    private long count(JsonNode data, String key) {
        var value = data.path(key);
        if (!value.isNumber() || !value.toString().matches("[0-9]+")) throw unavailable();
        return value.asLong();
    }

    private AccountException unavailable() {
        return new AccountException(AccountErrorCode.WITHDRAWAL_CHECK_UNAVAILABLE);
    }
}
