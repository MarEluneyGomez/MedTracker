package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.AccountDeactivationRequestDTO;
import com.medtracker.medtracker.dto.DoctorSummaryDTO;
import com.medtracker.medtracker.dto.FcmTokenUpdateRequestDTO;
import com.medtracker.medtracker.dto.UserDTO;
import com.medtracker.medtracker.dto.UserRegisterRequestDTO;
import com.medtracker.medtracker.dto.UserSummaryDTO;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.security.AuthenticatedUser;
import com.medtracker.medtracker.service.AccountDeactivationService;
import com.medtracker.medtracker.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    private final AccountDeactivationService accountDeactivationService;

    public UserController(UserService userService, AccountDeactivationService accountDeactivationService) {
        this.userService = userService;
        this.accountDeactivationService = accountDeactivationService;
    }

    // Registrar usuario (público). Solo PATIENT o CAREGIVER; sin rol, PATIENT.
    @PostMapping("/register")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody UserRegisterRequestDTO request) {
        User created = userService.register(request.toEntity());
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserDTO(created));
    }

    // Actualizar el token de FCM del usuario autenticado (el de Bearer; no se
    // acepta ningún id). Reemplaza al anterior y responde 204 sin body.
    @PutMapping("/me/fcm-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateFcmToken(@AuthenticationPrincipal AuthenticatedUser principal,
                               @Valid @RequestBody FcmTokenUpdateRequestDTO request) {
        userService.updateFcmToken(principal, request.getFcmToken());
    }

    // Baja de la propia cuenta (la del Bearer; no se acepta ningún id). Pide la
    // contraseña actual y responde 204 sin body.
    @PostMapping("/me/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal AuthenticatedUser principal,
                           @Valid @RequestBody AccountDeactivationRequestDTO request,
                           HttpServletRequest httpRequest) {
        accountDeactivationService.deactivate(principal, request.getPassword(), httpRequest.getRemoteAddr());
    }

    // Buscar usuario por ID. El propio perfil es completo; el de un vinculado
    // o contraparte de una cita va reducido, sin fcmToken. Otro usuario: 404.
    @GetMapping("/{id}")
    public ResponseEntity<?> findById(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        User user = userService.findVisible(principal, id);
        if (user.getId().equals(principal.id())) {
            return ResponseEntity.ok(new UserDTO(user));
        }
        return ResponseEntity.ok(new UserSummaryDTO(user));
    }

    // Con ?role=DOCTOR lista los médicos (id y nombre) para elegir uno al agendar
    // una cita. Con cualquier otro filtro, o sin filtro, devuelve solo al propio
    // usuario y a sus vinculados, en versión reducida (sin fcmToken).
    @GetMapping
    public ResponseEntity<List<?>> findAll(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @RequestParam(required = false) User.Role role) {
        if (role == User.Role.DOCTOR) {
            return ResponseEntity.ok(userService.findDoctors().stream().map(DoctorSummaryDTO::new).toList());
        }
        return ResponseEntity.ok(userService.findRelated(principal, role).stream().map(UserSummaryDTO::new).toList());
    }
}
