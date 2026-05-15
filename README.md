<div align="center">

# watermark-spring-boot-starter

一款面向 Spring Boot 2.7.x 的轻量级文件水印 Starter，支持图片、Excel、Word、PDF 文字水印。

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-8+-orange.svg)](https://www.oracle.com/java/technologies/javase/javase8-archive-downloads.html)
[![Maven](https://img.shields.io/badge/Maven-3.x-blue.svg)](https://maven.apache.org/)
[![PDFBox](https://img.shields.io/badge/PDF-Apache%20PDFBox-red.svg)](https://pdfbox.apache.org/)
[![POI](https://img.shields.io/badge/Office-Apache%20POI-yellow.svg)](https://poi.apache.org/)

## 🍟 如果你觉得有帮助，请点右上角 "Star" 支持一下谢谢

</div>

## 项目介绍

`watermark-spring-boot-starter` 用于在 Spring Boot 项目中快速接入文件水印能力。

项目只提供核心水印服务和自动装配，不内置 Controller，不绑定上传下载流程，也不侵入业务接口。业务系统可以根据自己的文件来源和输出方式，直接注入 `WatermarkService` 完成水印处理。

适合以下场景：

- 文件下载前追加水印
- 内部文档流转增加标识
- 图片、`.xlsx`、`.docx`、PDF 文件批量加水印
- SaaS 系统按租户、用户、时间动态生成水印
- 后台管理系统导出文件时增加追踪信息

## 核心特性

- 支持图片水印：`png`、`jpg`、`jpeg`、`bmp`
- 支持 Excel 水印：`xlsx`
- 支持 Word 水印：`docx`
- 支持 PDF 水印：`pdf`
- 支持中文水印字体
- 支持 Spring Boot 2.7.x 自动装配
- 支持按文件名自动识别处理器
- 支持字节数组和流式处理
- 支持水印文字、透明度、字号、颜色、旋转角度、位置配置
- 不支持旧版 Office 二进制格式 `.xls` 和 `.doc`
- Excel 默认锁定水印图片并保护工作表，降低水印被误删或拖动的概率

## 模块结构

```text
watermark-spring-boot-starter-parent/
├── pom.xml
├── watermark-core/
├── watermark-spring-boot-autoconfigure/
└── watermark-spring-boot-starter/
```

| 模块 | 说明 |
|---|---|
| `watermark-core` | 核心水印能力，不依赖 Spring |
| `watermark-spring-boot-autoconfigure` | Spring Boot 自动配置模块 |
| `watermark-spring-boot-starter` | 业务项目直接引入的 starter 聚合模块 |

## 环境要求

| 依赖 | 版本 |
|---|---|
| JDK | 8+ |
| Spring Boot | 2.7.18 |
| Maven | 3.x |

## 快速开始

### 1. 安装到本地仓库

```bash
mvn clean install
```

### 2. 引入依赖

```xml
<dependency>
    <groupId>com.watermark</groupId>
    <artifactId>watermark-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 3. 配置水印参数

```yaml
watermark:
  enabled: true
  text: watermark
  opacity: 0.3
  font-size: 40
  color: GRAY
  rotation: 45
  position: DIAGONAL
  font-path: classpath:/fonts/SourceHanSerifSC-Regular.otf
```

### 4. 注入服务使用

```java
import com.watermark.core.WatermarkService;
import org.springframework.stereotype.Service;

@Service
public class FileWatermarkService {

    private final WatermarkService watermarkService;

    public FileWatermarkService(WatermarkService watermarkService) {
        this.watermarkService = watermarkService;
    }

    public byte[] addWatermark(byte[] input, String fileName) throws Exception {
        return watermarkService.addWatermark(input, fileName);
    }
}
```

## 配置说明

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `watermark.enabled` | `true` | 是否启用自动配置 |
| `watermark.text` | `watermark` | 默认水印文字 |
| `watermark.opacity` | `0.3` | 水印透明度，范围 `0` 到 `1` |
| `watermark.font-size` | `40` | 水印字号 |
| `watermark.color` | `GRAY` | 水印颜色，支持 Java 颜色名、`#999999`、`0x999999` |
| `watermark.rotation` | `45` | 水印旋转角度 |
| `watermark.position` | `DIAGONAL` | 水印位置 |
| `watermark.font-path` | `classpath:/fonts/SourceHanSerifSC-Regular.otf` | 字体路径 |

字体路径支持以下形式：

```text
classpath:/fonts/SourceHanSerifSC-Regular.otf
file:D:/fonts/SourceHanSerifSC-Regular.otf
D:/fonts/SourceHanSerifSC-Regular.otf
/opt/fonts/SourceHanSerifSC-Regular.otf
```

## 水印位置

```java
public enum WatermarkPosition {
    CENTER,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    DIAGONAL
}
```

| 位置 | 说明 |
|---|---|
| `CENTER` | 中心位置单个水印 |
| `TOP_LEFT` | 左上角单个水印 |
| `TOP_RIGHT` | 右上角单个水印 |
| `BOTTOM_LEFT` | 左下角单个水印 |
| `BOTTOM_RIGHT` | 右下角单个水印 |
| `DIAGONAL` | 斜向平铺水印 |

## 核心 API

`WatermarkService` 是对外暴露的核心服务。

```java
public interface WatermarkService {

    byte[] addWatermark(byte[] input, String fileName) throws Exception;

    byte[] addWatermark(byte[] input, String fileName, WatermarkOptions options) throws Exception;

    void addWatermark(InputStream input, OutputStream output, String fileName) throws Exception;

    void addWatermark(InputStream input, OutputStream output, String fileName, WatermarkOptions options) throws Exception;
}
```

## 使用示例

### 使用默认配置

```java
byte[] output = watermarkService.addWatermark(inputBytes, "demo.pdf");
```

### 使用自定义配置

```java
import com.watermark.core.WatermarkOptions;
import com.watermark.core.WatermarkPosition;

WatermarkOptions options = WatermarkOptions.builder()
        .text("测试水印")
        .opacity(0.3f)
        .fontSize(32)
        .color("#999999")
        .rotation(45f)
        .position(WatermarkPosition.DIAGONAL)
        .build();

byte[] output = watermarkService.addWatermark(inputBytes, "demo.docx", options);
```

### 使用流式处理

```java
try (InputStream input = Files.newInputStream(Paths.get("D:/temp/demo.pdf"));
     OutputStream output = Files.newOutputStream(Paths.get("D:/temp/demo-watermarked.pdf"))) {
    watermarkService.addWatermark(input, output, "demo.pdf");
}
```

## 支持格式

| 类型 | 扩展名 | 实现方式 |
|---|---|---|
| 图片 | `png`、`jpg`、`jpeg`、`bmp` | 直接绘制到图片像素 |
| Excel | `xlsx` | 生成透明 PNG 水印图并嵌入工作表 |
| Word | `docx` | 写入页眉层水印，减少对正文排版的影响 |
| PDF | `pdf` | 生成透明水印图层并使用 Apache PDFBox 叠加到页面 |

不支持旧版 Office 二进制格式 `.xls` 和 `.doc`，也不计划支持。请在调用前将文件转换为 `.xlsx` 或 `.docx`。

## 实现说明

### 图片水印

图片水印会直接绘制到图片像素中。输出文件中不存在可单独选中的水印对象，因此相比 Office 文档更不容易被单独删除。

### Excel 水印

Excel 水印通过生成透明 PNG 水印图并嵌入工作表实现。当前实现会设置图片锁定并保护工作表，主要目标是降低误删概率和普通删除成本。

Excel 是可编辑文档格式，不能把这种方式理解为严格的防篡改能力。

### Word 水印

Word 水印写入页眉层中的 VML 形状。这样可以让水印位于页面背景层，减少对正文布局的影响，也更接近 Word 原生水印机制。

### PDF 水印

PDF 水印基于 Apache PDFBox 实现，不依赖 iText。当前实现会先使用 AWT 和配置字体生成透明水印图层，再把图层叠加到 PDF 页面上。

这种方式不依赖 PDFBox 直接嵌入中文字体，可以规避部分 OTF 中文字体无法通过 `PDType0Font` 写入文本的问题。代价是 PDF 水印本身是图片图层，不是可选中的 PDF 文本。

## 本地文件测试

项目提供了本地文件集成测试：

```text
watermark-core/src/test/java/com/watermark/core/LocalFileWatermarkIntegrationTest.java
```

未传入本地文件路径时，测试会自动跳过，不影响普通构建。

### PowerShell 示例

只测试图片：

```powershell
mvn -pl watermark-core test "-Dwatermark.test.image=D:\temp\watermark\demo.png"
```

测试全部支持格式：

```powershell
mvn -pl watermark-core test "-Dwatermark.test.image=D:\temp\watermark\demo.png" "-Dwatermark.test.excel=D:\temp\watermark\demo.xlsx" "-Dwatermark.test.pdf=D:\temp\watermark\demo.pdf" "-Dwatermark.test.word=D:\temp\watermark\demo.docx"
```

指定输出目录：

```powershell
mvn -pl watermark-core test "-Dwatermark.test.output=D:\temp\watermark-output" "-Dwatermark.test.image=D:\temp\watermark\demo.png"
```

### macOS / Linux 示例

```bash
mvn -pl watermark-core test \
  -Dwatermark.test.image=/tmp/watermark/demo.png \
  -Dwatermark.test.excel=/tmp/watermark/demo.xlsx \
  -Dwatermark.test.pdf=/tmp/watermark/demo.pdf \
  -Dwatermark.test.word=/tmp/watermark/demo.docx
```

默认输出目录：

```text
watermark-core/target/watermark-samples/
```

输出文件示例：

```text
image-watermarked.png
excel-watermarked.xlsx
pdf-watermarked.pdf
word-watermarked.docx
```

## 开发命令

构建全部模块：

```bash
mvn clean package
```

只测试核心模块：

```bash
mvn -pl watermark-core test
```

安装到本地 Maven 仓库：

```bash
mvn clean install
```

## 常见问题

<details>
<summary>这个项目会注册 Controller 吗？</summary>

不会。项目只暴露 `WatermarkService` Bean，不提供上传、下载或水印接口。业务系统可以根据自己的权限、文件存储和接口规范自行封装。
</details>

<details>
<summary>为什么 Excel 水印不能保证绝对不可删除？</summary>

Excel 是可编辑格式。任何写入工作簿的图片、形状或背景对象，理论上都可以被有编辑权限的用户移除。当前实现通过图片锁定和工作表保护降低误删概率，但不等同于文档防篡改。
</details>

<details>
<summary>Word 水印为什么写在页眉里？</summary>

Word 的水印通常由页眉中的 VML 形状承载。这样可以让水印位于页面背景层，减少对正文排版的影响，也更符合 Word 的水印模型。
</details>

<details>
<summary>PDF 中文水印报字体错误怎么办？</summary>

当前 PDF 水印通过透明图片图层写入，不再直接调用 PDFBox 的中文文本写入能力。如果仍然出现中文显示异常，请优先确认 `watermark.font-path` 指向的字体能被 Java AWT 正常加载，并且字体本身包含对应中文字符。
</details>

<details>
<summary>PowerShell 传 Maven `-D` 参数时报错怎么办？</summary>

PowerShell 下建议把整个 `-Dkey=value` 参数放进双引号：

```powershell
mvn -pl watermark-core test "-Dwatermark.test.image=D:\temp\watermark\demo.png"
```

不要写成：

```powershell
mvn -pl watermark-core test -Dwatermark.test.image="D:\temp\watermark\demo.png"
```
</details>

<details>
<summary>`mvn clean` 删除输出文件失败怎么办？</summary>

如果输出文件正在被 Word、WPS、Excel、图片查看器或资源管理器预览占用，`mvn clean` 可能无法删除 `target` 目录。

关闭占用文件的程序后重新执行即可。
</details>

## 贡献指南

欢迎提交 Issue 和 Pull Request。

建议贡献流程：

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feature/your-feature`
3. 提交变更：`git commit -m "Add your feature"`
4. 推送分支：`git push origin feature/your-feature`
5. 提交 Pull Request

提交代码前建议执行：

```bash
mvn clean package
```

## 许可证

本项目基于 [MIT License](LICENSE.txt) 许可证开源。

## 致谢

感谢以下开源项目：

- [Spring Boot](https://spring.io/projects/spring-boot) - Spring Boot 自动装配基础
- [Apache POI](https://poi.apache.org/) - Office 文档处理
- [Apache PDFBox](https://pdfbox.apache.org/) - PDF 文档处理
- [Project Lombok](https://projectlombok.org/) - Java 样板代码简化
- [JUnit 5](https://junit.org/junit5/) - 单元测试与集成测试

---

<div align="center">

**如果这个项目对你有帮助，请给它一个 ⭐ Star！**

</div>
