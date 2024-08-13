package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.common.ErrorApi;
import celulares.cordobacelulares.dtos.phonedto.OnePhoneDto;
import celulares.cordobacelulares.dtos.phonedto.PostNewPhone;
import celulares.cordobacelulares.dtos.phonedto.SearchPhone;
import celulares.cordobacelulares.dtos.whatsapp.MessageBodyDto;
import celulares.cordobacelulares.entities.PhoneEntity;
import celulares.cordobacelulares.services.PhoneService;
import celulares.cordobacelulares.services.implement.WhatsappService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
@RestController
@RequestMapping("/api/phones")
@CrossOrigin(origins = "http://vps-4306850-x.dattaweb.com:4200")
public class PhoneController {

    @Autowired
    private PhoneService phoneService;

    @Autowired
    private WhatsappService whatsappService;
    @GetMapping
    public ResponseEntity<Object> getAllPhones() {
        try {
            List<OnePhoneDto> phones = phoneService.getAllPhones();
            return ResponseEntity.ok(phones);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error obtaining phones", ex.getMessage()));
        }
    }
    @GetMapping("/search")
    public ResponseEntity<Object> searchPhone(String textsearch) {
        try {
            List<SearchPhone> phones = phoneService.searchPhone(textsearch);
            return ResponseEntity.ok(phones);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error obtaining phones", ex.getMessage()));
        }
    }
    @PostMapping("/sendmessage")
    public ResponseEntity<Object> sendMessage(@RequestBody MessageBodyDto messageBodyDto) {
        try {
            whatsappService.sendMessage(messageBodyDto);
            return ResponseEntity.ok().build();
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error sending message", ex.getMessage()));
        }
    }
    @GetMapping("/{id}")
    public ResponseEntity<Object> getPhoneById(@PathVariable Long id) {
        try {
            OnePhoneDto phone = phoneService.getPhoneById(id);
            return phone != null ? ResponseEntity.ok(phone) : ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.NOT_FOUND.value(), "Phone not found", "No phone found with id " + id));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error obtaining phone by ID", ex.getMessage()));
        }
    }
    @GetMapping("/brand/{brand}")
    public ResponseEntity<Object> getPhoneByBrand(@PathVariable String brand) {
        try {
            List<OnePhoneDto> phones = phoneService.getPhonesByBrand(brand);
            return phones != null && !phones.isEmpty() ? ResponseEntity.ok(phones) : ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.NOT_FOUND.value(), "Phones not found", "No phones found for brand " + brand));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error obtaining phones by brand", ex.getMessage()));
        }
    }
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> createPhone(@RequestBody PostNewPhone phone) {
        try {
            PhoneEntity savedPhone = phoneService.savePhone(phone);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedPhone);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error creating phone", ex.getMessage()));
        }
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deletePhone(@PathVariable Long id) {
        try {
            phoneService.deletePhone(id);
            return ResponseEntity.ok().build();
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error deleting phone", ex.getMessage()));
        }
    }
}