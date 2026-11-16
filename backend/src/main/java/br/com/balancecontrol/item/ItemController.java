package br.com.balancecontrol.item;

import br.com.balancecontrol.common.web.PageResponse;
import br.com.balancecontrol.item.dto.AdjustBalanceRequest;
import br.com.balancecontrol.item.dto.AdjustmentResponse;
import br.com.balancecontrol.item.dto.CreateItemRequest;
import br.com.balancecontrol.item.dto.ItemResponse;
import br.com.balancecontrol.item.dto.UpdateItemRequest;
import java.net.URI;
import javax.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/items")
public class ItemController {

    private final ItemService service;

    public ItemController(ItemService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ItemResponse> search(@RequestParam(required = false) String search,
                                             @RequestParam(required = false) Boolean active,
                                             Pageable pageable) {
        return service.search(search, active, pageable);
    }

    @GetMapping("/{id}")
    public ItemResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ItemResponse> create(@Valid @RequestBody CreateItemRequest request) {
        ItemResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/items/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ItemResponse update(@PathVariable long id, @Valid @RequestBody UpdateItemRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/adjustments")
    public ItemResponse adjust(@PathVariable long id, @Valid @RequestBody AdjustBalanceRequest request) {
        return service.adjustBalance(id, request);
    }

    @GetMapping("/{id}/adjustments")
    public PageResponse<AdjustmentResponse> adjustments(@PathVariable long id, Pageable pageable) {
        return service.adjustments(id, pageable);
    }
}
