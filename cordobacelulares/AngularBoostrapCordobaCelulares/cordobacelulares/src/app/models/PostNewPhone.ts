export interface PostNewPhone {
    idPhone?: number; // Opcional porque en creación no existe
    images: string[];
    mainCamera: string[];
    secondaryCamera: string;
    red: string;
    oficialWeb: string;
    screen: string[];
    processor: string;
    gpu: string;
    memory: string[];
    expansion: string;
    os: string;
    battery: string[];
    connectivity: string[];
    dimensions: string;
    security: string[];
    colors: number[]; // Lista de IDs de colores
    boxContents: number[]; // Lista de IDs de contenido de caja
    videoYoutube: string;
    idModel: number; // Referencia al modelo
  }
  