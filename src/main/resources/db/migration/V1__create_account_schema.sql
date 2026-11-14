-- 계정 서비스의 최초 테이블입니다. 엔티티 enum 값은 애플리케이션이 검증하므로 DB CHECK로 고정하지 않습니다.

create table account.account_consent_histories (
    marketing_agreed boolean not null,
    recorded_at timestamp(6) with time zone not null,
    account_id uuid not null,
    id uuid not null,
    source varchar(30) not null,
    primary key (id)
);

create table account.account_consents (
    marketing_agreed boolean not null,
    agreed_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    withdrawn_at timestamp(6) with time zone,
    account_id uuid not null,
    primary key (account_id)
);

create table account.account_interests (
    account_id uuid not null,
    id uuid not null,
    category varchar(40) not null,
    primary key (id),
    constraint uk_account_interests_category unique (account_id, category)
);

create table account.account_notification_settings (
    personalized_recommendation boolean not null,
    sale_start boolean not null,
    account_id uuid not null,
    primary key (account_id)
);

create table account.account_roles (
    granted_at timestamp(6) with time zone not null,
    account_id uuid not null,
    granted_by uuid,
    id uuid not null,
    role varchar(20) not null,
    primary key (id),
    constraint uk_account_roles_account_id_role unique (account_id, role)
);

create table account.account_withdrawals (
    version bigint not null,
    withdrawn_at timestamp(6) with time zone not null,
    account_id uuid not null,
    reason varchar(200),
    primary key (account_id)
);

create table account.accounts (
    created_at timestamp(6) with time zone not null,
    email_verified_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    version bigint not null,
    id uuid not null,
    status varchar(20) not null,
    name varchar(100) not null,
    email varchar(320) not null,
    password_hash varchar(255) not null,
    primary key (id),
    constraint uk_accounts_email unique (email)
);

create table account.addresses (
    is_default boolean not null,
    postal_code varchar(5) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    version bigint not null,
    account_id uuid not null,
    id uuid not null,
    recipient_phone varchar(20) not null,
    label varchar(50) not null,
    recipient_name varchar(100) not null,
    detail_address varchar(200),
    road_address varchar(200) not null,
    delivery_memo varchar(500),
    primary key (id)
);

create table account.auth_sessions (
    created_at timestamp(6) with time zone not null,
    expires_at timestamp(6) with time zone not null,
    revoked_at timestamp(6) with time zone,
    account_id uuid not null,
    id uuid not null,
    primary key (id)
);

create table account.email_verifications (
    attempts integer not null,
    request_count integer not null,
    consumed_at timestamp(6) with time zone,
    expires_at timestamp(6) with time zone not null,
    proof_expires_at timestamp(6) with time zone,
    requested_at timestamp(6) with time zone not null,
    verified_at timestamp(6) with time zone,
    window_started_at timestamp(6) with time zone not null,
    challenge_id uuid not null,
    proof_hash varchar(64),
    email varchar(320) not null,
    code_hash varchar(255) not null,
    primary key (email),
    constraint uk_email_verifications_challenge unique (challenge_id)
);

create table account.login_attempts (
    failures integer not null,
    window_started_at timestamp(6) with time zone not null,
    email varchar(320) not null,
    primary key (email)
);

create table account.notification_outbox (
    attempts integer not null,
    created_at timestamp(6) with time zone not null,
    next_attempt_at timestamp(6) with time zone not null,
    published_at timestamp(6) with time zone,
    id uuid not null,
    partition_key varchar(255) not null,
    payload text not null,
    topic varchar(255) not null,
    primary key (id)
);

create table account.password_reset_verifications (
    attempts integer not null,
    request_count integer not null,
    consumed_at timestamp(6) with time zone,
    expires_at timestamp(6) with time zone not null,
    proof_expires_at timestamp(6) with time zone,
    requested_at timestamp(6) with time zone not null,
    verified_at timestamp(6) with time zone,
    window_started_at timestamp(6) with time zone not null,
    account_id uuid,
    challenge_id uuid not null,
    proof_hash varchar(64),
    email varchar(320) not null,
    code_hash varchar(255) not null,
    primary key (email),
    constraint uk_password_reset_verifications_challenge unique (challenge_id)
);

create table account.refresh_tokens (
    consumed_at timestamp(6) with time zone,
    issued_at timestamp(6) with time zone not null,
    session_id uuid not null,
    hash varchar(64) not null,
    primary key (hash)
);

create table account.seller_address_entries (
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    version bigint not null,
    account_id uuid not null,
    id uuid not null,
    address_type varchar(20) not null,
    contact_phone varchar(20) not null,
    label varchar(50) not null,
    contact_name varchar(100) not null,
    detail_address varchar(255),
    postal_code varchar(255),
    road_address varchar(255),
    primary key (id)
);

create table account.seller_applications (
    attempt_number integer not null,
    bank_account_last_four varchar(4) not null,
    certificate_recheck boolean,
    dispatch_postal_code varchar(5) not null,
    return_postal_code varchar(5) not null,
    reviewed_at timestamp(6) with time zone,
    submitted_at timestamp(6) with time zone not null,
    version bigint not null,
    account_id uuid not null,
    id uuid not null,
    previous_application_id uuid,
    reviewed_by uuid,
    contact_phone varchar(20) not null,
    status varchar(20) not null,
    bank_name varchar(50) not null,
    contact_name varchar(100) not null,
    seller_display_name varchar(100),
    dispatch_detail_address varchar(200),
    dispatch_road_address varchar(200) not null,
    return_detail_address varchar(200),
    return_road_address varchar(200) not null,
    contact_email varchar(320) not null,
    rejection_reason varchar(1000),
    bank_account_encrypted varchar(255) not null,
    primary key (id),
    constraint uk_seller_application_attempt unique (account_id, attempt_number)
);

create table account.seller_certificate_reviews (
    viewed_at timestamp(6) with time zone not null,
    application_id uuid not null,
    id uuid not null,
    reviewer_id uuid not null,
    primary key (id),
    constraint uk_seller_certificate_reviewer unique (application_id, reviewer_id)
);

create table account.seller_certificates (
    content_length bigint not null,
    application_id uuid not null,
    file_name varchar(100) not null,
    object_key varchar(200) not null unique,
    primary key (application_id)
);

create table account.seller_correction_items (
    position integer not null check ((position>=0)),
    request_id uuid not null,
    field varchar(30) not null,
    reason varchar(500) not null,
    primary key (position, request_id)
);

create table account.seller_correction_requests (
    created_at timestamp(6) with time zone not null,
    deadline timestamp(6) with time zone not null,
    version bigint not null,
    account_id uuid not null,
    application_id uuid not null,
    id uuid not null,
    reviewer_id uuid not null,
    submitted_application_id uuid,
    state varchar(20) not null,
    primary key (id)
);

create table account.seller_profiles (
    bank_account_last_four varchar(4) not null,
    sales_restricted boolean,
    dispatch_postal_code varchar(5) not null,
    return_postal_code varchar(5) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    version bigint not null,
    account_id uuid not null,
    approved_application_id uuid not null unique,
    default_dispatch_entry_id uuid,
    default_return_entry_id uuid,
    contact_phone varchar(20) not null,
    logo_content_type varchar(40),
    store_introduction varchar(40),
    bank_name varchar(50) not null,
    account_holder_name varchar(100),
    contact_name varchar(100) not null,
    seller_display_name varchar(100),
    dispatch_detail_address varchar(200),
    dispatch_road_address varchar(200) not null,
    logo_object_key varchar(200),
    return_detail_address varchar(200),
    return_road_address varchar(200) not null,
    contact_email varchar(320) not null,
    bank_account_encrypted varchar(255) not null,
    primary key (account_id)
);

create table account.withdrawal_operations (
    next_at timestamp(6) with time zone not null,
    valid_until timestamp(6) with time zone not null,
    account_id uuid not null,
    operation_id uuid not null,
    state varchar(255) not null,
    primary key (account_id)
);

create index ix_account_consent_histories_account
   on account.account_consent_histories (account_id);

create index ix_addresses_account
   on account.addresses (account_id);

create index ix_auth_sessions_expires_at
   on account.auth_sessions (expires_at);

create index ix_auth_sessions_account
   on account.auth_sessions (account_id);

create index idx_outbox_pending
   on account.notification_outbox (published_at, next_attempt_at, created_at);

create index ix_refresh_tokens_session
   on account.refresh_tokens (session_id);

create index ix_seller_address_owner_type
   on account.seller_address_entries (account_id, address_type);

create index ix_seller_application_status
   on account.seller_applications (status, submitted_at);

create index ix_seller_correction_deadline
   on account.seller_correction_requests (state, deadline);

create index ix_seller_correction_application
   on account.seller_correction_requests (application_id, created_at);

create index ix_withdrawal_operations_due
   on account.withdrawal_operations (state, next_at);

alter table if exists account.account_consent_histories
   add constraint FK2ny6dfs694pqrl3493kx5iout
   foreign key (account_id)
   references account.accounts;

alter table if exists account.account_consents
   add constraint FKrdp37si5xb9wia00sdwdph7n5
   foreign key (account_id)
   references account.accounts;

alter table if exists account.account_interests
   add constraint FKi7qnnlh6w1mw4ctpywdfbg70d
   foreign key (account_id)
   references account.accounts;

alter table if exists account.account_notification_settings
   add constraint FKdnq7skwnlipfsai5xarkwu69u
   foreign key (account_id)
   references account.accounts;

alter table if exists account.account_roles
   add constraint fk_account_roles_account
   foreign key (account_id)
   references account.accounts;

alter table if exists account.addresses
   add constraint FKaf33clpk1s4svpok3x9egrvji
   foreign key (account_id)
   references account.accounts;

alter table if exists account.auth_sessions
   add constraint FKft3dicewbhbwku0huexv1j6a0
   foreign key (account_id)
   references account.accounts;

alter table if exists account.refresh_tokens
   add constraint FK9vkwkc18hlvf6mlrqt2i9vhlb
   foreign key (session_id)
   references account.auth_sessions;

alter table if exists account.seller_applications
   add constraint FKkjjfbfer9up59pec57upwkt8j
   foreign key (account_id)
   references account.accounts;

alter table if exists account.seller_correction_items
   add constraint FKlu4gs6iy20we99hq4nn8f7c4v
   foreign key (request_id)
   references account.seller_correction_requests;

alter table if exists account.seller_profiles
   add constraint FKn02c7saiy88kqe5396vbiin0
   foreign key (approved_application_id)
   references account.seller_applications;

alter table if exists account.seller_profiles
   add constraint FKhd5kpeg0kiw2nhbyyitgoao1
   foreign key (account_id)
   references account.accounts;
