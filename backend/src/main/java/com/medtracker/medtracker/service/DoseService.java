package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.repository.DoseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DoseService {

    private final DoseRepository doseRepository;

    // Registrar una nueva dosis
    public Dose register(Dose dose) {
        return doseRepository.save(dose);
    }

    // Buscar dosis por ID
    public Optional<Dose> findById(UUID id) {
        return doseRepository.findById(id);
    }

    // Listar todas las dosis
    public List<Dose> findAll() {
        return doseRepository.findAll();
    }

    // Listar dosis por recordatorio
    public List<Dose> findByReminder(UUID reminderId) {
        return doseRepository.findByReminderId(reminderId);
    }

    // Cambiar estado de la dosis (RN-03: una toma ya confirmada/omitida no puede volver a cambiar de estado)
    public Optional<Dose> changeStatus(UUID id, String status) {
        Optional<Dose> doseOpt = doseRepository.findById(id);
        if (doseOpt.isPresent()) {
            Dose dose = doseOpt.get();
            if (dose.getStatus() != Dose.DoseStatus.PENDING) {
                return Optional.empty();
            }
            try {
                Dose.DoseStatus newStatus = Dose.DoseStatus.valueOf(status.toUpperCase());
                dose.setStatus(newStatus);
                if (newStatus == Dose.DoseStatus.CONFIRMED) {
                    dose.setConfirmedAt(LocalDateTime.now());
                }
                doseRepository.save(dose);
                return Optional.of(dose);
            } catch (IllegalArgumentException e) {
                // Estado inválido
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
