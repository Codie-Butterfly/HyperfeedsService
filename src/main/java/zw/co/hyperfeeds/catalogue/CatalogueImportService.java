package zw.co.hyperfeeds.catalogue;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class CatalogueImportService {
    private static final List<String> REQUIRED = List.of("sku", "name", "category", "pack_size");
    private final JdbcClient jdbc;
    public CatalogueImportService(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ImportResult importCsv(String csv) {
        List<List<String>> rows = parseCsv(csv);
        if (rows.size() < 2) throw badRequest("CSV must contain a header and at least one product");
        List<String> header = rows.get(0).stream().map(v -> v.trim().toLowerCase(Locale.ROOT)).toList();
        for (String required : REQUIRED) if (!header.contains(required)) throw badRequest("Missing required column: " + required);
        int created = 0, updated = 0;
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            if (row.stream().allMatch(String::isBlank)) continue;
            if (row.size() != header.size()) throw badRequest("Row " + (index + 1) + " has " + row.size() + " columns; expected " + header.size());
            Map<String,String> values = new HashMap<>();
            for (int column=0; column<header.size(); column++) values.put(header.get(column), row.get(column).trim());
            for (String required : REQUIRED) if (values.get(required).isBlank()) throw badRequest("Row " + (index + 1) + " has blank " + required);
            UUID categoryId = category(values.get("category"));
            String sku = values.get("sku").toUpperCase(Locale.ROOT);
            boolean exists = jdbc.sql("select count(*) from products where sku=:sku").param("sku",sku).query(Integer.class).single() > 0;
            jdbc.sql("""
                    insert into products(id,sku,barcode,category_id,name,description,pack_size,image_url,published,active)
                    values(:id,:sku,:barcode,:category,:name,:description,:pack,:image,:published,:active)
                    on conflict(sku) do update set barcode=excluded.barcode,category_id=excluded.category_id,name=excluded.name,
                      description=excluded.description,pack_size=excluded.pack_size,image_url=excluded.image_url,
                      published=excluded.published,active=excluded.active,updated_at=now()
                    """).param("id",UUID.randomUUID()).param("sku",sku).param("barcode",nullable(values.get("barcode")))
                    .param("category",categoryId).param("name",values.get("name")).param("description",nullable(values.get("description")))
                    .param("pack",values.get("pack_size")).param("image",nullable(values.get("image_url")))
                    .param("published",bool(values,"published",false,index)).param("active",bool(values,"active",true,index)).update();
            if (exists) updated++; else created++;
        }
        return new ImportResult(created, updated, created + updated);
    }

    private UUID category(String name) {
        return jdbc.sql("select id from product_categories where lower(name)=lower(:name)").param("name",name).query(UUID.class).optional()
                .orElseGet(() -> jdbc.sql("insert into product_categories(id,name,active) values(:id,:name,true) returning id")
                        .param("id",UUID.randomUUID()).param("name",name).query(UUID.class).single());
    }

    private static boolean bool(Map<String,String> values,String key,boolean fallback,int row) {
        String value=values.get(key); if(value==null||value.isBlank()) return fallback;
        if(value.equalsIgnoreCase("true")) return true; if(value.equalsIgnoreCase("false")) return false;
        throw badRequest("Row " + (row + 1) + " has invalid " + key + "; use true or false");
    }
    private static String nullable(String value) { return value == null || value.isBlank() ? null : value; }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }

    static List<List<String>> parseCsv(String csv) {
        List<List<String>> rows=new ArrayList<>(); List<String> row=new ArrayList<>(); StringBuilder field=new StringBuilder(); boolean quoted=false;
        for(int i=0;i<csv.length();i++) {
            char c=csv.charAt(i);
            if(c=='\"') { if(quoted&&i+1<csv.length()&&csv.charAt(i+1)=='\"'){field.append('\"');i++;} else quoted=!quoted; }
            else if(c==','&&!quoted){row.add(field.toString());field.setLength(0);}
            else if((c=='\n'||c=='\r')&&!quoted){if(c=='\r'&&i+1<csv.length()&&csv.charAt(i+1)=='\n')i++;row.add(field.toString());field.setLength(0);rows.add(row);row=new ArrayList<>();}
            else field.append(c);
        }
        if(quoted) throw badRequest("CSV contains an unclosed quoted field");
        if(field.length()>0||!row.isEmpty()){row.add(field.toString());rows.add(row);}
        return rows;
    }

    public record ImportResult(int created,int updated,int total) {}
}
