package app.saikat.AnnotationProcessors;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.saikat.Annotations.WaspberryMessageHandler;

@SupportedAnnotationTypes("app.saikat.Annotations.WaspberryMessageHandler")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class WaspberryMessageHandlerProcessor extends AbstractProcessor {
    private Logger logger = LoggerFactory.getLogger(this.getClass().getSimpleName());

    private Messager messager;
    private ProcessingEnvironment processingEnv;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        this.processingEnv = processingEnv;
    }

    public static class Tuple<X, Y> {
        public X first;
        public Y second;

        public Tuple(X x, Y y) {
            first = x;
            second = y;
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        boolean hasElements = false;
        PackageElement packageElement;
        String packageName = null;
        ClassName className = null;

        Map<Element, List<Tuple<Element, String>>> listenerReferences = new HashMap<>();

        for (Element element : roundEnv.getElementsAnnotatedWith(WaspberryMessageHandler.class)) {
            messager.printMessage(Kind.OTHER, "Element:- " + element);

            if (element.getKind() != ElementKind.METHOD) {
                messager.printMessage(Kind.ERROR, "Can be only applied to public static methods");
                return true;
            }

            ExecutableElement method = (ExecutableElement) element;
            if (!method.getModifiers().containsAll(Arrays.asList(Modifier.PUBLIC, Modifier.STATIC))) {
                messager.printMessage(Kind.ERROR, "Can be only applied to public static methods");
                return true;
            }

            hasElements = true;

            Element elem = method;
            while (! (elem instanceof PackageElement)) {
                elem = elem.getEnclosingElement();
            }
            packageElement = (PackageElement) elem;
            String pkgName = packageElement.getQualifiedName().toString();
            String[] parsedPackage = pkgName.split("\\.");
            packageName = parsedPackage[0]+"."+parsedPackage[1]+"."+parsedPackage[2];
            className = ClassName.bestGuess(packageName+"."+"WaspberryMessageHandlers");
            messager.printMessage(Kind.OTHER, "PackageName:- " + packageName);
            messager.printMessage(Kind.OTHER, "className:- " + className);

            WaspberryMessageHandler messageHandler = element.getAnnotation(WaspberryMessageHandler.class);

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
                if(val == null) {
                    list = new ArrayList<>();
                } else {
                    list = val;
                }

                Tuple<Element, String> tuple = new Tuple<Element, String>(element.getEnclosingElement(), method.getSimpleName().toString());
                list.add(tuple);
                messager.printMessage(Kind.OTHER, "Inserted:- " + messageElement.getSimpleName().toString() +
                    ": ("+element.getEnclosingElement().getSimpleName().toString()+", "+
                    method.getSimpleName().toString()+")");
                return list;
            });

        }

        if (hasElements) {

            TypeVariableName first = TypeVariableName.get("X");  // <X>
            TypeVariableName second = TypeVariableName.get("Y"); // <Y>
            
            // Class<?>
            ParameterizedTypeName classWithWildcard = ParameterizedTypeName.get(ClassName.get(Class.class),
                WildcardTypeName.subtypeOf(Object.class));
            
            ClassName tupleClassName = ClassName.get("", className.simpleName(), "Tuple");
            // Tuple<X, Y>
            ParameterizedTypeName tuplePair = ParameterizedTypeName.get(tupleClassName, first, second);
            
            // Tuple<Class<?>, String>
            ParameterizedTypeName objectMethodPair = ParameterizedTypeName.get(tupleClassName, classWithWildcard, ClassName.get(String.class));

            // List<Tuple<Class<?>, String>
            ParameterizedTypeName listOfObjectMethodPairs = ParameterizedTypeName.get(ClassName.get(List.class), objectMethodPair);

            // Map<Class<?>, List<Tuple<Class<?>, String>>>
            ParameterizedTypeName mapElement = ParameterizedTypeName.get(ClassName.get(Map.class), classWithWildcard, listOfObjectMethodPairs);

            /**
             * public static class Tuple<X, Y> {
             *     public X first;
             *     public Y second;
             * 
             *     public Tuple(X first, Y second) {
             *         this.first = first;
             *         this.second = second;
             *     }
             * }
             */
            TypeSpec tupleClass = TypeSpec.classBuilder("Tuple")   
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addTypeVariables(Arrays.asList(first, second))
                .addField(first, "first", Modifier.PUBLIC)
                .addField(second, "second", Modifier.PUBLIC)
                .addMethod(MethodSpec.constructorBuilder()
                    .addParameter(first, "first")
                    .addParameter(second, "second")
                    .addStatement("this.first = first")
                    .addStatement("this.second = second")
                    .build()
                )
                .build();
                    
            
            /**
             * public class WaspberryMessaheHandlers {
             *     public static class Tuple<X, Y> {
             *         public X first;
             *         public Y second;
             * 
             *         public Tuple(X first, Y second) {
             *             this.first = first;
             *             this.second = second;
             *         }
             *     }
             *     private Map<Class<?>, List<Tuple<Class<?>, String>>> messageHandlers;
             * }
            */ 
            TypeSpec.Builder classBuilder = TypeSpec.classBuilder("WaspberryMessageHandlers")
                .addModifiers(Modifier.PUBLIC)
                .addType(tupleClass)
                .addField(mapElement, "messageHandlers", Modifier.PRIVATE);

            /**
             * public WaspberryMessageHandlers() {
             *     this.messageHandlers = new HaspMap<>();
             *     
             *     List<Tuple<Class<?>, String>> list_m1 = new ArrayList<>();
             *     list_m1.add(new Tuple(A.class, "abc"));
             *     list_m1.add(new Tuple(B.class, "abc"));
             * 
             *     this.messageHandlers.put(M1.class, list_m1);
             * 
             *     List<Tuple<Class<?>, String>> list_m2 = new ArrayList<>();
             *     list_m2.add(new Tuple(C.class, "abc"));
             *     list_m2.add(new Tuple(D.class, "abc"));
             *     this.messageHandlers.put(M2.class, list_m2);
             * 
             *     this.messageHandlers = Collections.unmodifiableMap(this.messageHandlers);
             * }
             */
            MethodSpec.Builder classConstructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement("this.messageHandlers = new $T<>()", HashMap.class);
            
            for (Map.Entry<Element, List<Tuple<Element, String>>> entry : listenerReferences.entrySet()) {
                String listName = "list_"+entry.getKey().getSimpleName().toString().toLowerCase();
                classConstructorBuilder.addStatement("$T $L = new $T<>()", listOfObjectMethodPairs, listName, ArrayList.class);
                for (Tuple<Element, String> tuple : entry.getValue()) {
                    classConstructorBuilder.addStatement("$L.add(new $L($T.class, \"$L\"))", listName, tupleClassName.simpleName(), tuple.first, tuple.second);
                }
                classConstructorBuilder.addStatement("this.messageHandlers.put($T.class, $N)", entry.getKey(), listName);
            }

            classConstructorBuilder.addStatement("this.messageHandlers = $T.unmodifiableMap(this.messageHandlers)", Collections.class);

            classBuilder.addMethod(classConstructorBuilder.build());

            /**
             * public Map<Class<?>, List<WaspberryMessageHandlers.Tuple<Class<?>, String>>> getHandlers() {
             *     return this.messageHandlers;
             * }
             */
            MethodSpec mapGetter = MethodSpec.methodBuilder("getHandlers")
                .addModifiers(Modifier.PUBLIC)
                .returns(mapElement)
                .addStatement("return this.messageHandlers", Collections.class)
                .build();

            classBuilder.addMethod(mapGetter);

            try {
                JavaFile.builder(packageName, classBuilder.build())
                    .skipJavaLangImports(true)
                    .indent("\t")
                    .build()
                    .writeTo(processingEnv.getFiler());

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return true;
    }
}