package app.saikat.AnnotationProcessors;

import java.io.File;
import java.io.IOException;
import java.util.List;
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
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.tools.Diagnostic.Kind;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.saikat.Annotations.ExportUrl;

@SupportedAnnotationTypes("app.saikat.Annotations.ExportUrl")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ExportUrlProcessor extends AbstractProcessor {

    private Logger logger = LoggerFactory.getLogger(this.getClass().getSimpleName());

    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        TypeSpec.Builder enumBuilder = TypeSpec.enumBuilder("Url");

        boolean hasEnumField = false;
        for (Element element : roundEnv.getElementsAnnotatedWith(ExportUrl.class)) {
            messager.printMessage(Kind.OTHER, "Element:- " + element);
            if (element.getKind() != ElementKind.METHOD) {
                messager.printMessage(Kind.ERROR, "Can be only applied to methods");
                return true;
            }
            hasEnumField = true;

            ExportUrl exportUrl = element.getAnnotation(ExportUrl.class);

            ExecutableElement method = (ExecutableElement) element;

            TypeMirror responseClass = method.getReturnType();

            try {
                DeclaredType genericReturnType = (DeclaredType) responseClass;
                responseClass = genericReturnType.getTypeArguments().get(0);
            } catch (ClassCastException e) {
                messager.printMessage(Kind.ERROR, "Return type of function " + element + " not of type Response<?>");
                continue;
            }

            VariableElement requestClass = method.getParameters().get(0);

            enumBuilder.addEnumConstant(exportUrl.name(), TypeSpec
                    .anonymousClassBuilder("$S, $T.class, $T.class", exportUrl.url(), requestClass, responseClass)
                    .addJavadoc("Generated from:- \n\tClass: $S,\n\tMethod: $S\n",
                            element.getEnclosingElement().toString(), element.getSimpleName())
                    .build());

        }

        if (hasEnumField) {
            ParameterizedTypeName classWithWildcard = ParameterizedTypeName.get(ClassName.get(Class.class),
                    WildcardTypeName.subtypeOf(Object.class));

            MethodSpec enumConstructor = MethodSpec.constructorBuilder().addParameter(String.class, "path")
                    .addParameter(classWithWildcard, "requestClass").addParameter(classWithWildcard, "responseClass")
                    .addStatement("this.path = path").addStatement("this.requestClass = requestClass")
                    .addStatement("this.responseClass = responseClass").build();

            MethodSpec pathGetter = MethodSpec.methodBuilder("getPath")
                    // .addModifiers(Modifier.PUBLIC)
                    .returns(String.class).addStatement("return this.path").build();

            MethodSpec requestClassGetter = MethodSpec.methodBuilder("getRequestClass").addModifiers(Modifier.PUBLIC)
                    .returns(classWithWildcard).addStatement("return this.requestClass").build();

            MethodSpec responseClassGetter = MethodSpec.methodBuilder("getResponseClass").addModifiers(Modifier.PUBLIC)
                    .returns(classWithWildcard).addStatement("return this.responseClass").build();

            enumBuilder.addModifiers(Modifier.PUBLIC).addJavadoc("Generated class. Not to be modified directly\n")
                    .addField(String.class, "path", Modifier.PRIVATE, Modifier.FINAL)
                    .addField(classWithWildcard, "requestClass", Modifier.PRIVATE, Modifier.FINAL)
                    .addField(classWithWildcard, "responseClass", Modifier.PRIVATE, Modifier.FINAL)
                    .addMethod(enumConstructor).addMethod(pathGetter).addMethod(responseClassGetter)
                    .addMethod(requestClassGetter);

            try {
                String projectDir = System.getenv("PROJECT_DIR");

                JavaFile.builder("app.saikat.UrlManagement", enumBuilder.build()).skipJavaLangImports(true).build()
                        .writeTo(new File(projectDir + "/UrlManagement/src/main/java"));

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }
}