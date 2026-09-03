package zw.co.hyperfeeds.catalogue;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class CatalogueService {
    private final JdbcClient jdbc;
    public CatalogueService(JdbcClient jdbc) { this.jdbc = jdbc; }

    public List<CategoryView> categories() {
        return jdbc.sql("select id,name,description,active from product_categories where active order by name").query(CategoryView.class).list();
    }

    public List<ProductView> products(UUID branchId, UUID categoryId, String search) {
        String term = search == null ? "" : search.trim().toLowerCase();
        return jdbc.sql("""
                select p.id,p.sku,p.barcode,p.category_id,c.name category_name,p.name,p.description,p.pack_size,p.image_url,
                       p.published,p.active,bp.amount,bp.currency,bi.on_hand,bi.reserved,
                       case when bi.on_hand is null then null else bi.on_hand-bi.reserved end available
                from products p join product_categories c on c.id=p.category_id
                left join branch_prices bp on bp.product_id=p.id and bp.branch_id=:branchId and bp.effective_to is null
                left join branch_inventory bi on bi.product_id=p.id and bi.branch_id=:branchId
                where p.published and p.active and c.active
                  and (cast(:categoryId as uuid) is null or p.category_id=:categoryId)
                  and (:term='' or lower(p.name) like '%'||:term||'%' or lower(p.sku) like '%'||:term||'%')
                order by p.name
                """).param("branchId", branchId).param("categoryId", categoryId).param("term", term).query(ProductView.class).list();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryView createCategory(CategoryInput input) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into product_categories(id,name,description,active) values(:id,:name,:description,:active)")
                .param("id", id).param("name", input.name().trim()).param("description", input.description()).param("active", input.active()).update();
        return new CategoryView(id, input.name().trim(), input.description(), input.active());
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ProductView createProduct(ProductInput input) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into products(id,sku,barcode,category_id,name,description,pack_size,image_url,published,active) values(:id,:sku,:barcode,:category,:name,:description,:pack,:image,:published,:active)")
                .param("id",id).param("sku",input.sku().trim().toUpperCase()).param("barcode",input.barcode())
                .param("category",input.categoryId()).param("name",input.name().trim()).param("description",input.description())
                .param("pack",input.packSize().trim()).param("image",input.imageUrl()).param("published",input.published()).param("active",input.active()).update();
        return managementProduct(id);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ProductView updateProduct(UUID id, ProductInput input) {
        int changed = jdbc.sql("update products set sku=:sku,barcode=:barcode,category_id=:category,name=:name,description=:description,pack_size=:pack,image_url=:image,published=:published,active=:active,updated_at=now() where id=:id")
                .param("id",id).param("sku",input.sku().trim().toUpperCase()).param("barcode",input.barcode())
                .param("category",input.categoryId()).param("name",input.name().trim()).param("description",input.description())
                .param("pack",input.packSize().trim()).param("image",input.imageUrl()).param("published",input.published()).param("active",input.active()).update();
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        return managementProduct(id);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER')")
    public void setPrice(UUID branchId, UUID productId, PriceInput input) {
        jdbc.sql("update branch_prices set effective_to=now() where branch_id=:branch and product_id=:product and effective_to is null")
                .param("branch",branchId).param("product",productId).update();
        jdbc.sql("insert into branch_prices(branch_id,product_id,amount,currency,effective_from) values(:branch,:product,:amount,:currency,now())")
                .param("branch",branchId).param("product",productId).param("amount",input.amount()).param("currency",input.currency().toUpperCase()).update();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or (hasRole('BRANCH_MANAGER') and @branchAccess.canAccess(authentication, #branchId))")
    public void setInventory(UUID branchId, UUID productId, InventoryInput input) {
        jdbc.sql("""
                insert into branch_inventory(branch_id,product_id,on_hand,reserved,low_stock_threshold)
                values(:branch,:product,:onHand,:reserved,:threshold)
                on conflict(branch_id,product_id) do update set on_hand=excluded.on_hand,reserved=excluded.reserved,
                  low_stock_threshold=excluded.low_stock_threshold,version=branch_inventory.version+1,updated_at=now()
                """).param("branch",branchId).param("product",productId).param("onHand",input.onHand())
                .param("reserved",input.reserved()).param("threshold",input.lowStockThreshold()).update();
    }

    private ProductView managementProduct(UUID id) {
        return jdbc.sql("""
                select p.id,p.sku,p.barcode,p.category_id,c.name category_name,p.name,p.description,p.pack_size,p.image_url,
                p.published,p.active,null::numeric amount,null::char(3) currency,null::numeric on_hand,
                null::numeric reserved,null::numeric available from products p join product_categories c on c.id=p.category_id where p.id=:id
                """).param("id", id).query(ProductView.class).single();
    }

    public record CategoryView(UUID id,String name,String description,boolean active) {}
    public record ProductView(UUID id,String sku,String barcode,UUID categoryId,String categoryName,String name,String description,
            String packSize,String imageUrl,boolean published,boolean active,BigDecimal amount,String currency,
            BigDecimal onHand,BigDecimal reserved,BigDecimal available) {}
    public record CategoryInput(String name,String description,boolean active) {}
    public record ProductInput(String sku,String barcode,UUID categoryId,String name,String description,String packSize,String imageUrl,boolean published,boolean active) {}
    public record PriceInput(BigDecimal amount,String currency) {}
    public record InventoryInput(BigDecimal onHand,BigDecimal reserved,BigDecimal lowStockThreshold) {}
}
