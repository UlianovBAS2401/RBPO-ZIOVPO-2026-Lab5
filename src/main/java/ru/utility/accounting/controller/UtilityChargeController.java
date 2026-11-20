package ru.utility.accounting.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.utility.accounting.dto.UtilityChargeDto;
import ru.utility.accounting.entity.User;
import ru.utility.accounting.entity.UtilityCharge;
import ru.utility.accounting.repository.UserRepository;
import ru.utility.accounting.service.UtilityChargeService;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/utility-charges")
public class UtilityChargeController {

    private final UtilityChargeService utilityChargeService;
    private final UserRepository userRepository;

    public UtilityChargeController(UtilityChargeService utilityChargeService, UserRepository userRepository) {
        this.utilityChargeService = utilityChargeService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<UtilityChargeDto>> getAllCharges(Authentication authentication) {
        boolean isStaff = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_OPERATOR"));

        List<UtilityCharge> list;
        if (isStaff) {
            list = utilityChargeService.findAll();
        } else {
            User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);
            if (currentUser != null) {
                list = utilityChargeService.findByUserId(currentUser.getId());
            } else {
                list = List.of();
            }
        }

        List<UtilityChargeDto> dtoList = new ArrayList<>();
        for (UtilityCharge c : list) {
            dtoList.add(toDto(c));
        }
        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN') or @securityService.isChargeOwner(authentication, #id)")
    public ResponseEntity<UtilityChargeDto> getChargeById(@PathVariable Long id) {
        UtilityCharge charge = utilityChargeService.findById(id);
        return ResponseEntity.ok(toDto(charge));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<UtilityChargeDto> createCharge(@RequestBody UtilityChargeDto dto) {
        UtilityCharge charge = toEntity(dto);
        UtilityCharge created = utilityChargeService.create(charge, dto.getPremisesId(), dto.getMeterId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    @PostMapping("/calculate")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<UtilityChargeDto> calculateCharge(
            @RequestParam Long meterId,
            @RequestParam String period) {
        UtilityCharge charge = utilityChargeService.calculateCharge(meterId, period);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(charge));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('USER') and @securityService.isChargeOwner(authentication, #id))")
    public ResponseEntity<UtilityChargeDto> payCharge(@PathVariable Long id) {
        UtilityCharge paid = utilityChargeService.payCharge(id);
        return ResponseEntity.ok(toDto(paid));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<UtilityChargeDto> patchCharge(@PathVariable Long id, @RequestBody UtilityChargeDto dto) {
        UtilityCharge updated = utilityChargeService.patch(id, dto);
        return ResponseEntity.ok(toDto(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCharge(@PathVariable Long id) {
        utilityChargeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private UtilityChargeDto toDto(UtilityCharge charge) {
        if (charge == null) {
            return null;
        }
        Long premisesId = (charge.getPremises() != null) ? charge.getPremises().getId() : null;
        Long meterId = (charge.getMeter() != null) ? charge.getMeter().getId() : null;
        return new UtilityChargeDto(
                charge.getId(),
                premisesId,
                meterId,
                charge.getPeriod(),
                charge.getVolume(),
                charge.getAmount(),
                charge.getStatus(),
                charge.getCreatedAt()
        );
    }

    private UtilityCharge toEntity(UtilityChargeDto dto) {
        if (dto == null) {
            return null;
        }
        UtilityCharge charge = new UtilityCharge();
        charge.setId(dto.getId());
        charge.setPeriod(dto.getPeriod());
        charge.setVolume(dto.getVolume());
        charge.setAmount(dto.getAmount());
        charge.setStatus(dto.getStatus());
        return charge;
    }
}
