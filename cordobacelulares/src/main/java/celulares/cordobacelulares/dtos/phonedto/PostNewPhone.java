package celulares.cordobacelulares.dtos.phonedto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostNewPhone {
    private Long idPhone;
    private List<String> images;
    private List<String> mainCamera;
    private String secondaryCamera;
    private String red;
    private String oficialWeb;
    private List<String> screen;
    private String processor;
    private String gpu;
    private List<String> memory;
    private String expansion;
    private String os;
    private List<String> battery;
    private List<String> connectivity;
    private String dimensions;
    private List<String> security;
    private List<Long> colors; // Simplificado a lista de Long
    private List<Long> boxContents; // Simplificado a lista de Long
    private String videoYoutube;
    private Long idModel; // Referencia al idModel
}
