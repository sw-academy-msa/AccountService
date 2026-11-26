-- Payment 결과를 기다리는 해지 취소 명령 ID입니다. 재요청에 같은 명령을 돌려줍니다.
alter table account.memberships add column cancel_withdraw_command_id uuid;
