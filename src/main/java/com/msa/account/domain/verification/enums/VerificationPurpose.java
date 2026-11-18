package com.msa.account.domain.verification.enums;

/** 메일의 제목과 안내 문구는 서버가 정한 목적만 사용합니다. 클라이언트가 목적을 지정할 수 없습니다. */
public enum VerificationPurpose {
    SIGNUP("[STOCKDROP] 회원가입 이메일 인증번호", "새로운 발견의 시작.", "이메일을 인증해 주세요.",
            "STOCKDROP에 오신 것을 환영해요.", "회원가입 화면에 아래 6자리 인증번호를 입력해 주세요.",
            "회원가입 화면", "회원가입 이메일 인증"),
    PASSWORD_RESET("[STOCKDROP] 비밀번호 재설정 인증번호", "계정을 안전하게.", "비밀번호를 다시 설정해 주세요.",
            "비밀번호 재설정을 요청하셨나요?", "비밀번호 재설정 화면에 아래 6자리 인증번호를 입력해 주세요.",
            "비밀번호 재설정 화면", "비밀번호 재설정");

    private final String subject;
    private final String headline;
    private final String action;
    private final String intro;
    private final String instruction;
    private final String screen;
    private final String description;
    VerificationPurpose(String subject, String headline, String action, String intro, String instruction, String screen, String description) {
        this.subject = subject; this.headline = headline; this.action = action; this.intro = intro;
        this.instruction = instruction; this.screen = screen; this.description = description;
    }
    public String subject() { return subject; }
    public String headline() { return headline; }
    public String action() { return action; }
    public String intro() { return intro; }
    public String instruction() { return instruction; }
    public String screen() { return screen; }
    public String description() { return description; }
}
