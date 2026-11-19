-- STOCKDROP 운영팀 1:1 문의. 주문 문의(판매자에게)와 별개이며 주문 id는 참고용으로만 보관한다
create table account.support_inquiries (
    id uuid not null,
    account_id uuid not null,
    category varchar(20) not null,
    title varchar(100) not null,
    body varchar(1000) not null,
    order_id uuid,
    status varchar(20) not null,
    answer varchar(2000),
    answered_by uuid,
    answered_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone not null,
    version bigint not null,
    primary key (id)
);
create index ix_support_inquiries_account on account.support_inquiries (account_id, created_at desc);
create index ix_support_inquiries_status on account.support_inquiries (status, created_at);
