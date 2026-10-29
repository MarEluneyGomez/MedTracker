package com.medtracker.medtracker;

import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.repository.DoseRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;

// El cambio de estado de una toma es un UPDATE condicional en la base, que un
// repositorio mockeado no ejecuta. Este helper lo imita sobre las dosis en
// memoria: aplica el cambio solo si el estado actual es uno de los esperados y
// devuelve las filas afectadas, igual que el repositorio real.
public final class DoseRepositoryStubs {

    private DoseRepositoryStubs() {
    }

    public static void statefulStatusUpdates(DoseRepository repository, Dose... doses) {
        Map<UUID, Dose> byId = new HashMap<>();
        for (Dose dose : doses) {
            byId.put(dose.getId(), dose);
            lenient().when(repository.findById(dose.getId())).thenReturn(Optional.of(dose));
        }
        lenient().when(repository.updateStatusIfCurrent(any(), anyCollection(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Dose dose = byId.get(invocation.<UUID>getArgument(0));
                    Collection<Dose.DoseStatus> expected = invocation.getArgument(1);
                    if (dose == null || !expected.contains(dose.getStatus())) {
                        return 0;
                    }
                    dose.setStatus(invocation.getArgument(2));
                    dose.setConfirmedAt(invocation.getArgument(3));
                    return 1;
                });
    }
}
