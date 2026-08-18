package zw.co.hyperfeeds.branches;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/branches")
class BranchController {
    private final BranchService branches;
    BranchController(BranchService branches) { this.branches = branches; }

    @GetMapping public List<BranchService.BranchView> list() { return branches.activeBranches(); }
    @GetMapping("/{id}") public BranchService.BranchView get(@PathVariable UUID id) { return branches.branch(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public BranchService.BranchView create(@Valid @RequestBody Request request) { return branches.create(request.input()); }
    @PutMapping("/{id}")
    public BranchService.BranchView update(@PathVariable UUID id, @Valid @RequestBody Request request) { return branches.update(id, request.input()); }

    record Request(@NotBlank @Size(max=40) String code, @NotBlank @Size(max=160) String name,
            @NotBlank String address, @NotBlank @Size(max=32) String phoneNumber,
            @Size(max=32) String whatsappNumber, @Email @Size(max=255) String emailAddress,
            @Size(max=255) String openingHours,
            boolean collectionEnabled, boolean active) {
        BranchService.BranchInput input() { return new BranchService.BranchInput(code,name,address,phoneNumber,whatsappNumber,emailAddress,openingHours,collectionEnabled,active); }
    }
}
