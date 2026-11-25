
; ============================================================
; INVENTRA - COMPUTER ORGANIZATION & ARCHITECTURE
; File: coa/coa_module.asm
; Assemble: nasm -f elf64 coa/coa_module.asm -o coa_module.o
; Link:     gcc -no-pie coa_module.o -o coa_module
; Run:      ./coa_module
; ============================================================

global main
extern printf
extern scanf

section .data

title db 10,"===== INVENTRA COA SIMULATOR =====",10,0
menu db 10,"1. Validate Fault Code",10
     db "2. Addition",10
     db "3. Shift-and-Add Multiplication",10
     db "4. Restoring Division",10
     db "5. Hexadecimal to BCD",10
     db "6. Control Unit Signals",10
     db "7. Pipeline Clock Calculation",10
     db "8. Exit",10
     db "Choice: ",0

fmt_int db "%d",0
fmt_num db "%ld",0
fmt_result db "Result: %ld",10,0
fmt_pair db "Quotient: %ld  Remainder: %ld",10,0
fmt_text db "%s",10,0
fmt_hex db "BCD digits: %ld",10,0

fault1 db "1 = Mouse Fault",0
fault2 db "2 = Keyboard Fault",0
fault3 db "3 = Display Fault",0
fault4 db "4 = Network Fault",0
invalid db "Invalid fault code.",0

sig_add db "ADD: RegWrite=1 MemRead=0 MemWrite=0 ALU=ADD",0
sig_load db "LOAD: RegWrite=1 MemRead=1 MemWrite=0 ALU=ADDRESS",0
sig_store db "STORE: RegWrite=0 MemRead=0 MemWrite=1 ALU=ADDRESS",0

pipe_msg db "5-stage pipeline: IF -> ID -> EX -> MEM -> WB",10
         db "Total cycles = Instructions + 4 (ideal pipeline)",10,0

newline db 10,0

section .bss
choice resd 1
a resq 1
b resq 1
result resq 1
quotient resq 1
remainder resq 1
fault resd 1

section .text

; [CO1] 80386 uses EAX, EBX, ECX and EDX.
; x86-64 extends these to RAX, RBX, RCX and RDX.
; CMP and JNE demonstrate conditional flag operations.

main:
    push rbp
    mov rbp,rsp

    lea rdi,[rel title]
    xor eax,eax
    call printf

menu_loop:
    lea rdi,[rel menu]
    xor eax,eax
    call printf

    lea rdi,[rel fmt_int]
    lea rsi,[rel choice]
    xor eax,eax
    call scanf

    mov eax,[rel choice]
    cmp eax,1
    je fault_validation
    cmp eax,2
    je addition
    cmp eax,3
    je multiplication
    cmp eax,4
    je division
    cmp eax,5
    je conversion
    cmp eax,6
    je control_unit
    cmp eax,7
    je pipeline
    cmp eax,8
    je exit_program

    jmp menu_loop

; [CO2] Fault validation
; Matching Java/C++ fault codes:
; 1 Mouse, 2 Keyboard, 3 Display, 4 Network.

fault_validation:
    lea rdi,[rel fmt_int]
    lea rsi,[rel fault]
    xor eax,eax
    call scanf

    mov eax,[rel fault]

    cmp eax,1
    je mouse_fault
    cmp eax,2
    je keyboard_fault
    cmp eax,3
    je display_fault
    cmp eax,4
    je network_fault
    jmp invalid_fault

mouse_fault:
    lea rsi,[rel fault1]
    jmp print_fault

keyboard_fault:
    lea rsi,[rel fault2]
    jmp print_fault

display_fault:
    lea rsi,[rel fault3]
    jmp print_fault

network_fault:
    lea rsi,[rel fault4]

print_fault:
    lea rdi,[rel fmt_text]
    xor eax,eax
    call printf
    jmp menu_loop

invalid_fault:
    lea rsi,[rel invalid]
    lea rdi,[rel fmt_text]
    xor eax,eax
    call printf
    jmp menu_loop

; [CO3] Computer arithmetic: addition

addition:
    call input_two

    mov rax,[rel a]
    add rax,[rel b]
    mov [rel result],rax

    lea rdi,[rel fmt_result]
    mov rsi,rax
    xor eax,eax
    call printf

    jmp menu_loop

; [CO3] Shift-and-add multiplication
; Unsigned binary multiplication

multiplication:
    call input_two

    mov rax,[rel a]
    mov rcx,[rel b]
    xor rdx,rdx

multiply_loop:
    test rcx,rcx
    jz multiply_done

    test rcx,1
    jz skip_add

    add rdx,rax

skip_add:
    shl rax,1
    shr rcx,1
    jmp multiply_loop

multiply_done:
    mov [rel result],rdx

    lea rdi,[rel fmt_result]
    mov rsi,rdx
    xor eax,eax
    call printf

    jmp menu_loop

; [CO3] Restoring division
; Quotient and remainder using binary long division.
; Input range: non-negative signed 64-bit integers.

division:
    call input_two

    mov r8,[rel a]
    mov r9,[rel b]

    test r9,r9
    jz division_error

    xor r10,r10
    xor r11,r11
    mov ecx,64

division_loop:
    shl r10,1
    bt r8,63
    jnc no_bit

    or r10,1

no_bit:
    shl r8,1
    shl r11,1

    cmp r10,r9
    jb no_subtract

    sub r10,r9
    or r11,1

no_subtract:
    dec ecx
    jnz division_loop

    lea rdi,[rel fmt_pair]
    mov rsi,r11
    mov rdx,r10
    xor eax,eax
    call printf

    jmp menu_loop

division_error:
    lea rdi,[rel fmt_text]
    lea rsi,[rel invalid]
    xor eax,eax
    call printf
    jmp menu_loop

; [CO3] 64-bit hexadecimal to decimal BCD conversion
; Input is a non-negative decimal integer representing
; the value to convert into packed decimal digits.

conversion:
    lea rdi,[rel fmt_num]
    lea rsi,[rel a]
    xor eax,eax
    call scanf

    mov rax,[rel a]
    mov rbx,10
    xor rcx,rcx

bcd_loop:
    xor rdx,rdx
    div rbx
    shl rcx,4
    or rcx,rdx

    test rax,rax
    jnz bcd_loop

    lea rdi,[rel fmt_hex]
    mov rsi,rcx
    xor eax,eax
    call printf

    jmp menu_loop

; [CO4] Control unit signals
; ADD, LOAD and STORE control signal generation.

control_unit:
    lea rdi,[rel fmt_text]
    lea rsi,[rel sig_add]
    xor eax,eax
    call printf

    lea rsi,[rel sig_load]
    xor eax,eax
    call printf

    lea rsi,[rel sig_store]
    xor eax,eax
    call printf

    jmp menu_loop

; [CO4] Five-stage instruction pipeline
; IF -> ID -> EX -> MEM -> WB
; Ideal cycles = N + 4 for N instructions.

pipeline:
    lea rdi,[rel pipe_msg]
    xor eax,eax
    call printf

    lea rdi,[rel fmt_num]
    lea rsi,[rel a]
    xor eax,eax
    call scanf

    mov rax,[rel a]
    add rax,4

    lea rdi,[rel fmt_result]
    mov rsi,rax
    xor eax,eax
    call printf

    jmp menu_loop

input_two:
    lea rdi,[rel fmt_num]
    lea rsi,[rel a]
    xor eax,eax
    call scanf

    lea rdi,[rel fmt_num]
    lea rsi,[rel b]
    xor eax,eax
    call scanf

    ret

exit_program:
    xor eax,eax
    leave
    ret
