package ru.utility.accounting.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.utility.accounting.dto.MeterReadingDto;
import ru.utility.accounting.entity.MeterReading;
import ru.utility.accounting.entity.User;
import ru.utility.accounting.repository.UserRepository;
import ru.utility.accounting.service.MeterReadingService;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/meter-readings")
public class MeterReadingController {

    private final MeterReadingService meterReadingService;
    private final UserRepository userRepository;

    public MeterReadingController(MeterReadingService meterReadingService, UserRepository userRepository) {
        this.meterReadingService = meterReadingService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<MeterReadingDto>> getAllReadings(Authentication authentication) {
        boolean isStaff = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_OPERATOR"));

        List<MeterReading> list;
        if (isStaff) {
            list = meterReadingService.findAll();
        } else {
            User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);
            if (currentUser != null) {
                list = meterReadingService.findByUserId(currentUser.getId());
            } else {
                list = List.of();
            }
        }

        List<MeterReadingDto> dtoList = new ArrayList<>();
        for (MeterReading r : list) {
            dtoList.add(toDto(r));
        }
        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN') or @securityService.isReadingOwner(authentication, #id)")
    public ResponseEntity<MeterReadingDto> getReadingById(@PathVariable Long id) {
        MeterReading reading = meterReadingService.findById(id);
        return ResponseEntity.ok(toDto(reading));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN') or (hasRole('USER') and @securityService.isMeterOwner(authentication, #dto.meterId))")
    public ResponseEntity<MeterReadingDto> createReading(@RequestBody MeterReadingDto dto) {
        MeterReading reading = toEntity(dto);
        MeterReading created = meterReadingService.create(reading, dto.getMeterId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<MeterReadingDto> verifyReading(
            @PathVariable Long id,
            @RequestParam(defaultValue = "VERIFIED") String status) {
        MeterReading verified = meterReadingService.verify(id, status);
        return ResponseEntity.ok(toDto(verified));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<MeterReadingDto> patchReading(@PathVariable Long id, @RequestBody MeterReadingDto dto) {
        MeterReading updated = meterReadingService.patch(id, dto);
        return ResponseEntity.ok(toDto(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteReading(@PathVariable Long id) {
        meterReadingService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private MeterReadingDto toDto(MeterReading reading) {
        if (reading == null) {
            return null;
        }
        Long meterId = (reading.getMeter() != null) ? reading.getMeter().getId() : null;
        return new MeterReadingDto(
                reading.getId(),
                meterId,
                reading.getPeriod(),
                reading.getReadingValue(),
                reading.getVerificationStatus(),
                reading.getRecordedAt()
        );
    }

    private MeterReading toEntity(MeterReadingDto dto) {
        if (dto == null) {
            return null;
        }
        MeterReading reading = new MeterReading();
        reading.setId(dto.getId());
        reading.setPeriod(dto.getPeriod());
        reading.setReadingValue(dto.getReadingValue());
        reading.setVerificationStatus(dto.getVerificationStatus());
        return reading;
    }
}
