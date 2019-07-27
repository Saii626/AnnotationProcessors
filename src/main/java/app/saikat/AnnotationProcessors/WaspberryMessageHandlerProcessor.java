package app.saikat.AnnotationProcessors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic.Kind;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;

import app.saikat.Annotations.WaspberryMessageHandler;
import app.saikat.PojoCollections.CommonObjects.Tuple;
import app.saikat.PojoCollections.CommonObjects.WebsocketMessageHandlers;

@SupportedAnnotationTypes("app.saikat.Annotations.WaspberryMessageHandler")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class WaspberryMessageHandlerProcessor extends AbstractProcessor {

    private Messager messager;
    private ProcessingEnvironment processingEnv;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        this.processingEnv = processingEnv;
    }

    private static boolean processed = false;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        if (processed || roundEnv.getElementsAnnotatedWith(WaspberryMessageHandler.class).isEmpty()) {
            messager.printMessage(Kind.OTHER, "processed: " + processed);
            return true;
        }

        PackageElement packageElement;
        String packageName = null;
        ClassName className = null;
        // processed = true;
        // String pkg = processingEnv.getOptions().get("wsMessageHandlerLoc");
        // String packageName = "app.saikat." + pkg;
        // ClassName className = ClassName.bestGuess(packageName + "." +
        // "WaspberryMessageHandlers");
        // messager.printMessage(Kind.OTHER, "PackageName:- " + packageName);
        // messager.printMessage(Kind.OTHER, "className:- " + className);

        Map<Element, List<Tuple<Element, String>>> listenerReferences = new HashMap<>();

        for (Element element : roundEnv.getElementsAnnotatedWith(WaspberryMessageHandler.class)) {
            messager.printMessage(Kind.OTHER, "Element:- " + element);

            if (element.getKind() != ElementKind.METHOD) {
                messager.printMessage(Kind.ERROR, "Can be only applied to public static methods");
                return true;
            }

            ExecutableElement method = (ExecutableElement) element;
            if (!method.getModifiers().contains(Modifier.PUBLIC)) {
                messager.printMessage(Kind.ERROR, "Can be only applied to public methods");
                return true;
            }

            Element elem = method;
            while (!(elem instanceof PackageElement)) {
                elem = elem.getEnclosingElement();
            }
            packageElement = (PackageElement) elem;
            String pkgName = packageElement.getQualifiedName().toString();
            String[] parsedPackage = pkgName.split("\\.");
            packageName = parsedPackage[0] + "." + parsedPackage[1] + "." + parsedPackage[2];
            className = ClassName.bestGuess(packageName + "." + "WaspberryMessageHandlers");
            messager.printMessage(Kind.OTHER, "PackageName:- " + packageName);
            messager.printMessage(Kind.OTHER, "className:- " + className);

            WaspberryMessageHandler messageHandler = element.getAnnotation(WaspberryMessageHandler.class);
            if (messageHandler == null) {
                continue;
            }

            List<? extends VariableElement> requestElements = method.getParameters();
            VariableElement messageElement;
            if (requestElements.size() == 1) {
                messageElement = requestElements.get(0);
            } else if (requestElements.size() == 2) {
                messageElement = requestElements.get(1);
            } else {
                messager.printMessage(Kind.ERROR,
                        "Can have methods signatures of type 1: public static void(Object), public static void(Object, Object) only");
                return true;
            }

            listenerReferences.compute(messageElement, (key, val) -> {
                List<Tuple<Element, String>> list;
                if (val == null) {
                    list = new ArrayList<>();
                } else {
                    list = val;
                }

                Tuple<Element, String> tuple = new Tuple<Element, String>(element.getEnclosingElement(),
                        method.getSimpleName().toString());
                list.add(tuple);
                messager.printMessage(Kind.OTHER,
                        "Inserted:- " + messageElement.asType() + ": ("
                                + element.getEnclosingElement().getSimpleName().toString() + ", "
                                + method.getSimpleName().toString() + ")");
                return list;
            });

        }

        // Class<?>
        ParameterizedTypeName classWithWildcard = ParameterizedTypeName.get(ClassName.get(Class.class),
                WildcardTypeName.subtypeOf(Object.class));
        // Tuple
        ClassName tupleClassName = ClassName.get(Tuple.class);

        // Tuple<Class<?>, String>
        ParameterizedTypeName objectMethodPair = ParameterizedTypeName.get(tupleClassName, classWithWildcard,
                ClassName.get(String.class));

        // List<Tuple<Class<?>, String>>
        ParameterizedTypeName listOfObjectMethodPairs = ParameterizedTypeName.get(ClassName.get(List.class),
                objectMethodPair);

        /**
         * public class WaspberryMessaheHandlers extends WebsocketMessageHandlers { }
         */
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder("WaspberryMessageHandlers").addModifiers(Modifier.PUBLIC)
                .superclass(WebsocketMessageHandlers.class);

        /**
         * public WaspberryMessageHandlers() { 
         *     this.messageHandlers = new HaspMap<>();
         * 
         *     List<Tuple<Class<?>, String>> list_m1 = new ArrayList<>();
         *     list_m1.add(new Tuple<Class<?>, String>(A.class, "abc"));
         *     list_m1.add(new Tuple(B.class, "abc"));
         * 
         *     this.messageHandlers.put(M1.class, list_m1);
         * 
         *     List<Tuple<Class<?>, String>> list_m2 = new ArrayList<>();
         *     list_m2.add(new Tuple<Class<?>, String>(B.class, "test"));
         *     this.messageHandlers.put(M2.class, list_m2);
         * 
         *     this.messageHandlers = Collections.unmodifiableMap(this.messageHandlers);
         * }
         */
        MethodSpec.Builder classConstructorBuilder = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

        for (Map.Entry<Element, List<Tuple<Element, String>>> entry : listenerReferences.entrySet()) {
            String listName = "list_" + entry.getKey().getSimpleName().toString().toLowerCase();
            classConstructorBuilder.addStatement("$T $L = new $T<>()", listOfObjectMethodPairs, listName,
                    ArrayList.class);
            for (Tuple<Element, String> tuple : entry.getValue()) {
                classConstructorBuilder.addStatement("$L.add(new $T($T.class, \"$L\"))", listName, objectMethodPair,
                        tuple.first, tuple.second);
            }
            classConstructorBuilder.addStatement("this.handlers.put($T.class, $N)", entry.getKey(), listName);
            classConstructorBuilder.addCode("\n");
        }

        classConstructorBuilder.addStatement("this.handlers = $T.unmodifiableMap(this.handlers)", Collections.class);

        classBuilder.addMethod(classConstructorBuilder.build());

        try {
            JavaFile.builder(packageName, classBuilder.build()).skipJavaLangImports(true).indent("    ").build()
                    .writeTo(processingEnv.getFiler());

        } catch (IOException e) {
            e.printStackTrace();
        }

        return true;
    }
}