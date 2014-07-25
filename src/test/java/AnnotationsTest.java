import net.specialattack.loader.Service;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import sun.misc.IOUtils;

import java.io.IOException;
import java.util.List;

@Service
public class AnnotationsTest {

    public static void main(String[] params) throws IOException {
        inspect("AnnotationsTest.class");
        inspect("net/specialattack/loader/Service.class");
    }

    private static void inspect(String clazz) throws IOException {
        System.out.println("Inspecting class '" + clazz + "'");
        byte[] data = IOUtils.readFully(AnnotationsTest.class.getClassLoader().getResourceAsStream(clazz), -1, true);

        ClassReader reader = new ClassReader(data);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);

        List<AnnotationNode> annotations = (List<AnnotationNode>) node.invisibleAnnotations;
        if (annotations != null) {
            System.out.println("Annotations:");
            for (AnnotationNode annotation : annotations) {
                System.out.println(annotation.desc);
                if (annotation.desc.equals(Type.getDescriptor(Service.class))) {
                    System.out.println("Class is a service!");
                    break;
                }
            }
        } else {
            System.out.println("No annotations present");
        }
    }

}
