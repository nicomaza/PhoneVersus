package celulares.cordobacelulares.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "phone")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonIdentityInfo(
        generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "idPhone"
)
public class PhoneEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_phone")
    private Long idPhone;

    @ElementCollection
    private List<String> images;

    @ElementCollection
    private List<String> mainCamera;

    private String secondaryCamera;

    private String oficialWeb;

    private String red;

    @ElementCollection
    private List<String> screen;

    private String processor;
    private String gpu;

    @ElementCollection
    private List<String> memory;

    private String expansion;
    private String os;

    @ElementCollection
    private List<String> battery;

    @ElementCollection
    private List<String> connectivity;

    private String dimensions;

    @ElementCollection
    private List<String> security;

    @ManyToMany
    @JoinTable(
            name = "phone_colors",
            joinColumns = @JoinColumn(name = "id_phone"),
            inverseJoinColumns = @JoinColumn(name = "id_color")
    )
    private List<ColorEntity> colors;

    @ManyToMany
    @JoinTable(
            name = "phone_box_contents",
            joinColumns = @JoinColumn(name = "id_phone"),
            inverseJoinColumns = @JoinColumn(name = "id_content")
    )
    private List<BoxContentsEntity> boxContents;


    private String videoYoutube;

    @ManyToOne
    @JoinColumn(name = "id_model", nullable = false)
    private ModelEntity model;


}
