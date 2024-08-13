package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.phonedto.OnePhoneDto;
import celulares.cordobacelulares.dtos.phonedto.PostNewPhone;
import celulares.cordobacelulares.dtos.phonedto.SearchPhone;
import celulares.cordobacelulares.entities.PhoneEntity;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public interface PhoneService {

    List<OnePhoneDto> getAllPhones();

    List<SearchPhone> searchPhone(String textSearch);

    OnePhoneDto getPhoneById(Long id);

    PhoneEntity savePhone(PostNewPhone phone);

     void deletePhone(Long id);

    List<OnePhoneDto> getPhonesByBrand(String brand);
}
