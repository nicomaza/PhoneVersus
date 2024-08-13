package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.phonedto.OnePhoneDto;
import celulares.cordobacelulares.dtos.phonedto.PostNewPhone;
import celulares.cordobacelulares.dtos.phonedto.SearchPhone;
import celulares.cordobacelulares.entities.BoxContentsEntity;
import celulares.cordobacelulares.entities.ColorEntity;
import celulares.cordobacelulares.entities.ModelEntity;
import celulares.cordobacelulares.entities.PhoneEntity;
import celulares.cordobacelulares.repository.BoxContentJPA;
import celulares.cordobacelulares.repository.ColorJPA;
import celulares.cordobacelulares.repository.ModelJPA;
import celulares.cordobacelulares.repository.PhoneJPA;
import celulares.cordobacelulares.services.BrandService;
import celulares.cordobacelulares.services.PhoneService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PhoneServiceImpl implements PhoneService {

    @Autowired
    PhoneJPA phoneRepository;

    @Autowired
    ModelMapper modelMapper;

    @Autowired
    BrandService brandService;

    @Autowired
    ModelJPA modelJPA;

    @Autowired
    ColorJPA colorJPA;

    @Autowired
    BoxContentJPA boxContentJPA;


    @Override
    public List<OnePhoneDto> getAllPhones() {
        // Obtener todas las entidades de teléfono
        List<PhoneEntity> listEntity = phoneRepository.findAll();

        // Convertir la lista de PhoneEntity a lista de OnePhoneDto
        List<OnePhoneDto> onePhoneDtoList = listEntity.stream()
                .map(phoneEntity -> {
                    OnePhoneDto phoneDto = modelMapper.map(phoneEntity, OnePhoneDto.class);
                    if (phoneEntity.getModel() != null) {
                        phoneDto.setModel(phoneEntity.getModel().getModelName());
                        // Asumiendo que ModelEntity tiene un método getBrand() para obtener la marca
                        phoneDto.setBrand(phoneEntity.getModel().getBrand().getBrandName());
                    }

                    // Convertir la lista de ColorEntity a una lista de cadenas
                    List<String> colors = phoneEntity.getColors().stream()
                            .map(ColorEntity::toString)  // Asegúrate de tener un método toString en ColorEntity
                            .collect(Collectors.toList());
                    phoneDto.setColors(colors);
                    return phoneDto;
                })
                .collect(Collectors.toList());

        return onePhoneDtoList;
    }

    @Override
    public List<SearchPhone> searchPhone(String textSearch) {
        List<PhoneEntity> listEntity = phoneRepository.findAll();
        // Dividir el texto de búsqueda en palabras
        String[] searchTerms = textSearch.toLowerCase().split("\\s+");
        // Filtrar las entidades según el texto de búsqueda
        List<PhoneEntity> filteredEntities = listEntity.stream()
                .filter(phoneEntity -> {
                    String modelName = phoneEntity.getModel().getModelName().toLowerCase();
                    String brandName = phoneEntity.getModel().getBrand().getBrandName().toLowerCase();

                    // Verificar si todas las palabras están presentes en el nombre del modelo o en la marca
                    return java.util.Arrays.stream(searchTerms).allMatch(term ->
                            modelName.contains(term) || brandName.contains(term)
                    );
                })
                .toList();
        // Convertir la lista filtrada de PhoneEntity a una lista de OnePhoneDto
        List<SearchPhone> onePhoneDtoList = filteredEntities.stream()
                .map(phoneEntity -> {
                    SearchPhone phoneDto = modelMapper.map(phoneEntity, SearchPhone.class);
                    if (phoneEntity.getModel() != null) {
                        phoneDto.setModel(phoneEntity.getModel().getModelName());
                        phoneDto.setBrand(phoneEntity.getModel().getBrand().getBrandName());
                    }
                    return phoneDto;
                })
                .limit(5)
                .collect(Collectors.toList());

        return onePhoneDtoList;
    }

    @Override
    public OnePhoneDto getPhoneById(Long id) {
        PhoneEntity phoneEntity = phoneRepository.findById(id).orElse(null);
        if (phoneEntity == null) {
            return null; // O maneja el caso cuando no se encuentra la entidad
        }

        OnePhoneDto phoneDto = modelMapper.map(phoneEntity, OnePhoneDto.class);

        // Set modelName and brandName manually
        if (phoneEntity.getModel() != null) {
            phoneDto.setModel(phoneEntity.getModel().getModelName());
            // Assuming ModelEntity has a method getBrand() or similar
            phoneDto.setBrand(phoneEntity.getModel().getBrand().getBrandName());
        }

        return phoneDto;
    }

    @Override
    public PhoneEntity savePhone(PostNewPhone phoneDTO) {
        PhoneEntity phoneEntity = new PhoneEntity();
        phoneEntity.setIdPhone(phoneDTO.getIdPhone());
        phoneEntity.setImages(phoneDTO.getImages());
        phoneEntity.setMainCamera(phoneDTO.getMainCamera());
        phoneEntity.setSecondaryCamera(phoneDTO.getSecondaryCamera());
        phoneEntity.setRed(phoneDTO.getRed());
        phoneEntity.setScreen(phoneDTO.getScreen());
        phoneEntity.setProcessor(phoneDTO.getProcessor());
        phoneEntity.setGpu(phoneDTO.getGpu());
        phoneEntity.setMemory(phoneDTO.getMemory());
        phoneEntity.setExpansion(phoneDTO.getExpansion());
        phoneEntity.setOs(phoneDTO.getOs());
        phoneEntity.setBattery(phoneDTO.getBattery());
        phoneEntity.setConnectivity(phoneDTO.getConnectivity());
        phoneEntity.setDimensions(phoneDTO.getDimensions());
        phoneEntity.setSecurity(phoneDTO.getSecurity());
        phoneEntity.setVideoYoutube(phoneDTO.getVideoYoutube());

        // Mapeo de List<Long> a List<ColorEntity>
        List<ColorEntity> colorEntities = phoneDTO.getColors().stream()
                .map(id -> colorJPA.findById(id)
                        .orElseThrow(() -> new RuntimeException("Color not found")))
                .collect(Collectors.toList());
        phoneEntity.setColors(colorEntities);

        // Mapeo de List<Long> a List<BoxContentsEntity>
        List<BoxContentsEntity> boxContentsEntities = phoneDTO.getBoxContents().stream()
                .map(id -> boxContentJPA.findById(id)
                        .orElseThrow(() -> new RuntimeException("Box Content not found")))
                .collect(Collectors.toList());
        phoneEntity.setBoxContents(boxContentsEntities);

        // Asigna el modelo al teléfono
        ModelEntity modelEntity = modelJPA.findById(phoneDTO.getIdModel())
                .orElseThrow(() -> new RuntimeException("Model not found"));
        phoneEntity.setModel(modelEntity);

        return phoneRepository.save(phoneEntity);
    }


    public void deletePhone(Long id) {
        phoneRepository.deleteById(id);
    }

    @Override
    public List<OnePhoneDto> getPhonesByBrand(String brand) {
        // Obtener todas las entidades filtradas por la marca


        List<PhoneEntity> phoneEntities = phoneRepository.findPhonesByBrandName(brand);

        // Convertir las entidades a DTOs
        List<OnePhoneDto> onePhoneDtoList = phoneEntities.stream()
                .map(phoneEntity -> {
                    // Mapear la entidad a DTO
                    OnePhoneDto phoneDto = modelMapper.map(phoneEntity, OnePhoneDto.class);
                    // Establecer el nombre del modelo y la marca en el DTO
                    if (phoneEntity.getModel() != null) {
                        phoneDto.setModel(phoneEntity.getModel().getModelName());
                        phoneDto.setBrand(phoneEntity.getModel().getBrand().getBrandName());
                    }
                    return phoneDto;
                })
                .collect(Collectors.toList());

        return onePhoneDtoList;
    }

}
