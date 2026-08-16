package zw.co.hyperfeeds.catalogue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/catalogue")
class CatalogueController {
    private final CatalogueService catalogue;
    CatalogueController(CatalogueService catalogue) { this.catalogue = catalogue; }

    @GetMapping("/categories") List<CatalogueService.CategoryView> categories() { return catalogue.categories(); }
    @GetMapping("/products") List<CatalogueService.ProductView> products(@RequestParam UUID branchId,
            @RequestParam(required=false) UUID categoryId, @RequestParam(required=false) String q) { return catalogue.products(branchId,categoryId,q); }

    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED)
    CatalogueService.CategoryView category(@Valid @RequestBody CategoryRequest r) { return catalogue.createCategory(new CatalogueService.CategoryInput(r.name(),r.description(),r.active())); }
    @PostMapping("/products") @ResponseStatus(HttpStatus.CREATED)
    CatalogueService.ProductView product(@Valid @RequestBody ProductRequest r) { return catalogue.createProduct(r.input()); }
    @PutMapping("/products/{id}") CatalogueService.ProductView product(@PathVariable UUID id,@Valid @RequestBody ProductRequest r) { return catalogue.updateProduct(id,r.input()); }
    @PutMapping("/branches/{branchId}/products/{productId}/price") @ResponseStatus(HttpStatus.NO_CONTENT)
    void price(@PathVariable UUID branchId,@PathVariable UUID productId,@Valid @RequestBody PriceRequest r) { catalogue.setPrice(branchId,productId,new CatalogueService.PriceInput(r.amount(),r.currency())); }
    @PutMapping("/branches/{branchId}/products/{productId}/inventory") @ResponseStatus(HttpStatus.NO_CONTENT)
    void inventory(@PathVariable UUID branchId,@PathVariable UUID productId,@Valid @RequestBody InventoryRequest r) { catalogue.setInventory(branchId,productId,new CatalogueService.InventoryInput(r.onHand(),r.reserved(),r.lowStockThreshold())); }

    record CategoryRequest(@NotBlank @Size(max=120) String name,String description,boolean active) {}
    record ProductRequest(@NotBlank @Size(max=80) String sku,@Size(max=100) String barcode,@NotNull UUID categoryId,
            @NotBlank @Size(max=200) String name,String description,@NotBlank @Size(max=80) String packSize,String imageUrl,
            boolean published,boolean active) {
        CatalogueService.ProductInput input(){return new CatalogueService.ProductInput(sku,barcode,categoryId,name,description,packSize,imageUrl,published,active);}
    }
    record PriceRequest(@NotNull @DecimalMin("0.00") BigDecimal amount,@NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency) {}
    record InventoryRequest(@NotNull @DecimalMin("0") BigDecimal onHand,@NotNull @DecimalMin("0") BigDecimal reserved,
            @NotNull @DecimalMin("0") BigDecimal lowStockThreshold) {
        @AssertTrue(message="reserved must not exceed onHand") boolean isStockValid(){return onHand==null||reserved==null||reserved.compareTo(onHand)<=0;}
    }
}
