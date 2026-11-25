-- 회원 자격 projection과 수신 기록만 추가하며 기존 계정 데이터는 수정하지 않습니다.
alter table account.notification_outbox add column failed_at timestamp(6) with time zone;
alter table account.notification_outbox add column last_failure_code varchar(64);
alter table account.withdrawal_operations add column membership_fenced boolean not null default false;

create table account.memberships (
    account_id uuid not null,
    subscription_id uuid not null,
    generation bigint not null,
    state varchar(32) not null,
    period_start timestamp(6) with time zone,
    period_end timestamp(6) with time zone,
    entitlement_until timestamp(6) with time zone,
    grace_until timestamp(6) with time zone,
    auto_renew boolean not null,
    plan_version varchar(40) not null,
    price_amount bigint not null,
    currency varchar(3) not null,
    cycle_number bigint not null,
    source_version bigint not null,
    command_version bigint not null,
    cancel_command_id uuid,
    cancel_generation bigint,
    row_version bigint not null,
    updated_at timestamp(6) with time zone not null,
    primary key (account_id),
    constraint fk_memberships_account foreign key (account_id) references account.accounts (id)
);

create index ix_memberships_expiry on account.memberships (state, entitlement_until);

create table account.membership_event_receipts (
    event_id uuid not null,
    subscription_id uuid not null,
    source_version bigint not null,
    payload_hash varchar(64) not null,
    processed_at timestamp(6) with time zone not null,
    primary key (event_id),
    constraint uk_membership_event_receipts_version unique (subscription_id, source_version)
);

create table account.membership_notice_receipts (
    id uuid not null,
    subscription_id uuid not null,
    generation bigint not null,
    cycle_number bigint not null,
    notice_type varchar(40) not null,
    scheduled_for timestamp(6) with time zone not null,
    created_at timestamp(6) with time zone not null,
    primary key (id),
    constraint uk_membership_notice_receipts_cycle
        unique (subscription_id, generation, cycle_number, notice_type)
);
