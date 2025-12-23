package at.tori.dmr.analyzer

import at.tori.dmr.client.GitLabApiClient
import at.tori.dmr.domain.FileChange
import at.tori.dmr.domain.SearchResult
import mu.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

data class Symbol(
  val name: String,
  val type: at.tori.dmr.analyzer.SymbolType,
  val filePath: String,
  val isPublic: Boolean = true
)

enum class SymbolType {
  FUNCTION,
  CLASS,
  INTERFACE,
  DATA_CLASS,
  OBJECT,
  PROPERTY,
  CONSTANT
}

data class DependencyInfo(
  val symbol: at.tori.dmr.analyzer.Symbol,
  val usages: List<at.tori.dmr.domain.SearchResult>,
  val affectedFiles: Set<String>
) {
  val usageCount: Int get() = usages.size
  val hasExternalUsages: Boolean get() = affectedFiles.any { it != symbol.filePath }
}

// 변경된 파일이 사용하는 외부 심볼
data class UsedDependencyInfo(
  val sourceFile: String,  // 변경된 파일 경로
  val usedSymbols: List<at.tori.dmr.analyzer.UsedSymbol>,  // 사용하는 외부 심볼들
  val externalFiles: Set<String>  // 의존하는 외부 파일들
)

data class UsedSymbol(
  val name: String,
  val type: at.tori.dmr.analyzer.UsedSymbolType,
  val definitionFile: String?,  // 정의된 파일 (찾을 수 있는 경우)
  val lineNumber: Int  // 변경된 파일에서 사용된 라인 번호
)

enum class UsedSymbolType {
  FUNCTION_CALL,  // 함수 호출
  CLASS_USAGE,    // 클래스 사용
  PROPERTY_ACCESS, // 프로퍼티 접근
  IMPORT          // import 문
}

@Service
class DependencyAnalyzer(
  private val gitLabApiClient: at.tori.dmr.client.GitLabApiClient
) {

  /**
   * 변경된 파일들에 대한 의존성 분석
   *
   * @param projectId GitLab 프로젝트 ID
   * @param changedFiles 변경된 파일 목록
   * @param targetBranch 검색할 대상 브랜치
   * @return 의존성 정보 목록
   */
  suspend fun analyze(
    projectId: Long,
    changedFiles: List<at.tori.dmr.domain.FileChange>,
    targetBranch: String
  ): List<at.tori.dmr.analyzer.DependencyInfo> {
    _root_ide_package_.at.tori.dmr.analyzer.logger.info { "의존성 분석 중: ${changedFiles.size}개 파일" }

    val allSymbols = mutableListOf<at.tori.dmr.analyzer.Symbol>()

    // 각 변경된 파일에서 심볼 추출
    for (file in changedFiles) {
      if (file.deletedFile) continue

      val symbols = extractSymbolsFromDiff(file)
      allSymbols.addAll(symbols)
      _root_ide_package_.at.tori.dmr.analyzer.logger.debug { "심볼 추출 완료: ${file.filePath}에서 ${symbols.size}개" }
    }

    _root_ide_package_.at.tori.dmr.analyzer.logger.info { "분석할 심볼 발견: 총 ${allSymbols.size}개" }

    // 각 심볼의 사용하는 부분 검색
    val dependencyInfoList = mutableListOf<at.tori.dmr.analyzer.DependencyInfo>()

    for (symbol in allSymbols) {
      try {
        val searchResults = gitLabApiClient.searchCode(
          projectId = projectId,
          query = symbol.name,
          ref = targetBranch
        )

        // 심볼이 정의된 파일은 제외
        val externalUsages = searchResults.filter { it.path != symbol.filePath }

        if (externalUsages.isNotEmpty()) {
          val affectedFiles = externalUsages.map { it.path }.toSet()

          dependencyInfoList.add(
            _root_ide_package_.at.tori.dmr.analyzer.DependencyInfo(
              symbol = symbol,
              usages = externalUsages,
              affectedFiles = affectedFiles
            )
          )

          _root_ide_package_.at.tori.dmr.analyzer.logger.debug {
            "${symbol.type} '${symbol.name}'이(가) ${affectedFiles.size}개의 다른 파일에서 사용 중"
          }
        }
      } catch (e: Exception) {
        _root_ide_package_.at.tori.dmr.analyzer.logger.warn { "심볼 '${symbol.name}' 검색 실패: ${e.message}" }
      }
    }

    _root_ide_package_.at.tori.dmr.analyzer.logger.info { "의존성 발견: ${dependencyInfoList.size}개 심볼" }
    return dependencyInfoList
  }

  // 변경된 파일이 사용하는 외부 심볼 추출
  suspend fun analyzeUsedDependencies(
    projectId: Long,
    changedFiles: List<at.tori.dmr.domain.FileChange>,
    targetBranch: String
  ): List<at.tori.dmr.analyzer.UsedDependencyInfo> {
    _root_ide_package_.at.tori.dmr.analyzer.logger.info { "역방향 의존성 분석 중: ${changedFiles.size}개 파일" }

    val usedDependencies = mutableListOf<at.tori.dmr.analyzer.UsedDependencyInfo>()

    for (file in changedFiles) {
      if (file.deletedFile) continue

      val usedSymbols = extractUsedSymbolsFromDiff(file)
      if (usedSymbols.isEmpty()) continue

      logger.debug { "${file.filePath}에서 ${usedSymbols.size}개 외부 심볼 사용" }

      // 각 심볼의 정의 위치 검색
      val symbolsWithDefinition = mutableListOf<UsedSymbol>()
      val externalFiles = mutableSetOf<String>()

      for (usedSymbol in usedSymbols) {
        try {
          val searchResults = gitLabApiClient.searchCode(
            projectId = projectId,
            query = usedSymbol.name,
            ref = targetBranch
          )

          // 정의 파일 찾기 (변경된 파일 제외)
          val definitionFile = searchResults.firstOrNull { it.path != file.filePath }?.path

          if (definitionFile != null) {
            externalFiles.add(definitionFile)
            symbolsWithDefinition.add(usedSymbol.copy(definitionFile = definitionFile))
          } else {
            symbolsWithDefinition.add(usedSymbol)
          }
        } catch (e: Exception) {
          logger.warn { "심볼 '${usedSymbol.name}' 정의 검색 실패: ${e.message}" }
          symbolsWithDefinition.add(usedSymbol)
        }
      }

      if (symbolsWithDefinition.isNotEmpty()) {
        usedDependencies.add(
          UsedDependencyInfo(
            sourceFile = file.filePath,
            usedSymbols = symbolsWithDefinition,
            externalFiles = externalFiles
          )
        )
      }
    }

    logger.info { "역방향 의존성 발견: ${usedDependencies.size}개 파일이 외부 심볼 사용" }
    return usedDependencies
  }

  /**
   * 파일의 diff에서 심볼 추출 (Kotlin & Java 지원)
   *
   * Kotlin:
   * - 함수 정의: fun functionName(...
   * - 클래스 정의: class ClassName
   * - 인터페이스 정의: interface InterfaceName
   * - 데이터 클래스: data class DataClassName
   * - 오브젝트: object ObjectName
   * - 프로퍼티: val/var propertyName
   *
   * Java:
   * - 메서드 정의: returnType methodName(...
   * - 클래스 정의: class ClassName
   * - 인터페이스 정의: interface InterfaceName
   * - Enum 정의: enum EnumName
   * - 필드 정의: type fieldName
   *
   *  TODO: msa에서 lambda를 사용한다면.. js나 python도 추가해야할지?
   */
  private fun extractSymbolsFromDiff(file: FileChange): List<Symbol> {
    val symbols = mutableListOf<Symbol>()
    val lines = file.diff.lines()

    val isKotlinFile = file.extension == "kt"
    val isJavaFile = file.extension == "java"

    for (line in lines) {
      // 추가된 라인만 확인 (+로 시작)
      if (!line.startsWith("+")) {
        continue
      }

      val content = line.substring(1).trim()

      // 빈 라인, 주석 패스
      if (content.isEmpty() || content.startsWith("//") || content.startsWith("/*")) {
        continue
      }

      when {
        isKotlinFile -> extractKotlinSymbols(content, file.filePath, symbols)
        isJavaFile -> extractJavaSymbols(content, file.filePath, symbols)
      }
    }

    return symbols.distinctBy { it.name }
  }

  private fun extractKotlinSymbols(content: String, filePath: String, symbols: MutableList<Symbol>) {
    // 함수 정의 추출
    val functionRegex =
      """(public |private |protected |internal )?(suspend )?(inline )?(fun)\s+(<[^>]+>)?\s*(\w+)\s*\(""".toRegex()
    functionRegex.find(content)?.let {
      val visibility = it.groupValues[1].trim()
      val name = it.groupValues[6]
      symbols.add(
        Symbol(
          name = name,
          type = SymbolType.FUNCTION,
          filePath = filePath,
          isPublic = visibility.isEmpty() || visibility == "public"
        )
      )
    }

    // 데이터 클래스 정의 추출 (class보다 먼저 확인해야 함)
    val dataClassRegex = """(public |private |protected |internal )?(data\s+class)\s+(\w+)""".toRegex()
    dataClassRegex.find(content)?.let {
      val visibility = it.groupValues[1].trim()
      val name = it.groupValues[3]
      symbols.add(
        Symbol(
          name = name,
          type = SymbolType.DATA_CLASS,
          filePath = filePath,
          isPublic = visibility.isEmpty() || visibility == "public"
        )
      )
    }

    // 클래스 정의 추출
    val classRegex = """(public |private |protected |internal )?(abstract |open |sealed )?(class)\s+(\w+)""".toRegex()
    classRegex.find(content)?.let {
      val visibility = it.groupValues[1].trim()
      val name = it.groupValues[4]
      symbols.add(
        Symbol(
          name = name,
          type = SymbolType.CLASS,
          filePath = filePath,
          isPublic = visibility.isEmpty() || visibility == "public"
        )
      )
    }

    // 인터페이스 정의 추출
    val interfaceRegex = """(public |private |protected |internal )?(interface)\s+(\w+)""".toRegex()
    interfaceRegex.find(content)?.let {
      val visibility = it.groupValues[1].trim()
      val name = it.groupValues[3]
      symbols.add(
        Symbol(
          name = name,
          type = SymbolType.INTERFACE,
          filePath = filePath,
          isPublic = visibility.isEmpty() || visibility == "public"
        )
      )
    }

    // 오브젝트 정의 추출
    val objectRegex = """(public |private |protected |internal )?(object)\s+(\w+)""".toRegex()
    objectRegex.find(content)?.let {
      val visibility = it.groupValues[1].trim()
      val name = it.groupValues[3]
      symbols.add(
        Symbol(
          name = name,
          type = SymbolType.OBJECT,
          filePath = filePath,
          isPublic = visibility.isEmpty() || visibility == "public"
        )
      )
    }

    // 프로퍼티 정의 추출 (val/var)
    val propertyRegex = """(public |private |protected |internal )?(val|var|const\s+val)\s+(\w+)\s*[:=]""".toRegex()
    propertyRegex.find(content)?.let {
      val visibility = it.groupValues[1].trim()
      val name = it.groupValues[3]
      val isConst = it.groupValues[2].contains("const")
      symbols.add(
        Symbol(
          name = name,
          type = if (isConst) SymbolType.CONSTANT else SymbolType.PROPERTY,
          filePath = filePath,
          isPublic = visibility.isEmpty() || visibility == "public"
        )
      )
    }
  }

  private fun extractJavaSymbols(content: String, filePath: String, symbols: MutableList<Symbol>) {
    // Java 클래스 정의 추출
    val javaClassRegex = """(public |private |protected )?(static )?(final )?(abstract )?(class)\s+(\w+)""".toRegex()
    javaClassRegex.find(content)?.let {
      val visibility = it.groupValues[1].trim()
      val name = it.groupValues[6]
      symbols.add(
        Symbol(
          name = name,
          type = SymbolType.CLASS,
          filePath = filePath,
          isPublic = visibility.isEmpty() || visibility == "public"
        )
      )
    }

    // Java 인터페이스 정의 추출
    val javaInterfaceRegex = """(public |private |protected )?(interface)\s+(\w+)""".toRegex()
    javaInterfaceRegex.find(content)?.let {
      val visibility = it.groupValues[1].trim()
      val name = it.groupValues[3]
      symbols.add(
        Symbol(
          name = name,
          type = SymbolType.INTERFACE,
          filePath = filePath,
          isPublic = visibility.isEmpty() || visibility == "public"
        )
      )
    }

    // Java Enum 정의 추출
    val javaEnumRegex = """(public |private |protected )?(enum)\s+(\w+)""".toRegex()
    javaEnumRegex.find(content)?.let {
      val visibility = it.groupValues[1].trim()
      val name = it.groupValues[3]
      symbols.add(
        Symbol(
          name = name,
          type = SymbolType.CLASS, // Enum도 CLASS로 취급
          filePath = filePath,
          isPublic = visibility.isEmpty() || visibility == "public"
        )
      )
    }

    // java 메서드 정의 추출
    val javaMethodRegex =
      """(public |private |protected )?(static )?(final )?(synchronized )?(\w+(<[^>]+>)?)\s+(\w+)\s*\(""".toRegex()
    javaMethodRegex.find(content)?.let {
      val visibility = it.groupValues[1].trim()
      val methodName = it.groupValues[7]

      if (!content.contains("class $methodName")) {
        symbols.add(
          Symbol(
            name = methodName,
            type = SymbolType.FUNCTION,
            filePath = filePath,
            isPublic = visibility.isEmpty() || visibility == "public"
          )
        )
      }
    }

    // Java 필드 정의 추출
    val javaFieldRegex = """(public |private |protected )?(static )?(final )?(\w+(<[^>]+>)?)\s+(\w+)\s*[=;]""".toRegex()
    javaFieldRegex.find(content)?.let {
      val visibility = it.groupValues[1].trim()
      val isStatic = it.groupValues[2].trim().isNotEmpty()
      val isFinal = it.groupValues[3].trim().isNotEmpty()
      val fieldName = it.groupValues[6]

      // 클래스/인터페이스 정의가 아닌 경우만
      if (!content.contains("class ") && !content.contains("interface ")) {
        symbols.add(
          Symbol(
            name = fieldName,
            type = if (isStatic && isFinal) SymbolType.CONSTANT else SymbolType.PROPERTY,
            filePath = filePath,
            isPublic = visibility.isEmpty() || visibility == "public"
          )
        )
      }
    }
  }

  private fun extractUsedSymbolsFromDiff(file: FileChange): List<UsedSymbol> {
    val usedSymbols = mutableListOf<UsedSymbol>()
    val lines = file.diff.lines()

    val isKotlinFile = file.extension == "kt"
    val isJavaFile = file.extension == "java"

    var lineNumber = 0
    for (line in lines) {
      if (line.startsWith("+")) {
        lineNumber++
      }

      if (!line.startsWith("+")) {
        continue
      }

      val content = line.substring(1).trim()

      if (content.isEmpty() || content.startsWith("//") || content.startsWith("/*")) {
        continue
      }

      when {
        isKotlinFile -> extractKotlinUsedSymbols(content, lineNumber, usedSymbols)
        isJavaFile -> extractJavaUsedSymbols(content, lineNumber, usedSymbols)
      }
    }

    return usedSymbols.distinctBy { it.name }
  }

  private fun extractKotlinUsedSymbols(content: String, lineNumber: Int, usedSymbols: MutableList<UsedSymbol>) {
    // Import 문 추출
    val importRegex = """^import\s+([a-zA-Z0-9_.]+)\.(\w+)""".toRegex()
    importRegex.find(content)?.let {
      val symbolName = it.groupValues[2]
      usedSymbols.add(
        UsedSymbol(
          name = symbolName,
          type = UsedSymbolType.IMPORT,
          definitionFile = null,
          lineNumber = lineNumber
        )
      )
    }

    // 클래스 인스턴스 생성: `ClassName(` (함수 호출보다 먼저 확인)
    val classUsageRegex = """([A-Z]\w+)\s*\(""".toRegex()
    classUsageRegex.findAll(content).forEach {
      val className = it.groupValues[1]
      usedSymbols.add(
        UsedSymbol(
          name = className,
          type = UsedSymbolType.CLASS_USAGE,
          definitionFile = null,
          lineNumber = lineNumber
        )
      )
    }

    // 함수 호출 추출: `functionName(` (소문자로 시작하는 것만)
    val functionCallRegex = """([a-z]\w+)\s*\(""".toRegex()
    functionCallRegex.findAll(content).forEach {
      val functionName = it.groupValues[1]
      // 예약어 제외
      if (functionName !in kotlinKeywords) {
        usedSymbols.add(
          UsedSymbol(
            name = functionName,
            type = UsedSymbolType.FUNCTION_CALL,
            definitionFile = null,
            lineNumber = lineNumber
          )
        )
      }
    }

    // 프로퍼티/메서드 접근: `object.property` 또는 `object.method()`
    val propertyAccessRegex = """\.(\w+)""".toRegex()
    propertyAccessRegex.findAll(content).forEach {
      val propertyName = it.groupValues[1]
      if (propertyName !in kotlinKeywords) {
        usedSymbols.add(
          UsedSymbol(
            name = propertyName,
            type = UsedSymbolType.PROPERTY_ACCESS,
            definitionFile = null,
            lineNumber = lineNumber
          )
        )
      }
    }
  }

  private fun extractJavaUsedSymbols(content: String, lineNumber: Int, usedSymbols: MutableList<UsedSymbol>) {
    // Import 문 추출
    val importRegex = """^import\s+([a-zA-Z0-9_.]+)\.(\w+)""".toRegex()
    importRegex.find(content)?.let {
      val symbolName = it.groupValues[2]
      usedSymbols.add(
        UsedSymbol(
          name = symbolName,
          type = UsedSymbolType.IMPORT,
          definitionFile = null,
          lineNumber = lineNumber
        )
      )
    }

    // 클래스 사용: `new ClassName(` (함수 호출보다 먼저 확인)
    val classUsageRegex = """new\s+([A-Z]\w+)\s*\(""".toRegex()
    classUsageRegex.findAll(content).forEach {
      val className = it.groupValues[1]
      usedSymbols.add(
        UsedSymbol(
          name = className,
          type = UsedSymbolType.CLASS_USAGE,
          definitionFile = null,
          lineNumber = lineNumber
        )
      )
    }

    // 함수/메서드 호출 (소문자로 시작하는 것만)
    val methodCallRegex = """([a-z]\w+)\s*\(""".toRegex()
    methodCallRegex.findAll(content).forEach {
      val methodName = it.groupValues[1]
      if (methodName !in javaKeywords) {
        usedSymbols.add(
          UsedSymbol(
            name = methodName,
            type = UsedSymbolType.FUNCTION_CALL,
            definitionFile = null,
            lineNumber = lineNumber
          )
        )
      }
    }

    // 프로퍼티/메서드 접근
    val propertyAccessRegex = """\.(\w+)""".toRegex()
    propertyAccessRegex.findAll(content).forEach {
      val propertyName = it.groupValues[1]
      if (propertyName !in javaKeywords) {
        usedSymbols.add(
          UsedSymbol(
            name = propertyName,
            type = UsedSymbolType.PROPERTY_ACCESS,
            definitionFile = null,
            lineNumber = lineNumber
          )
        )
      }
    }
  }

  companion object {
    // 예약어 목록
    private val kotlinKeywords = setOf(
      "if", "else", "when", "for", "while", "do", "return", "break", "continue",
      "this", "super", "true", "false", "null", "is", "in", "as", "val", "var",
      "fun", "class", "object", "interface", "package", "import", "throw", "try",
      "catch", "finally", "get", "set", "let", "run", "apply", "also", "with"
    )

    private val javaKeywords = setOf(
      "if", "else", "switch", "case", "for", "while", "do", "return", "break", "continue",
      "this", "super", "true", "false", "null", "instanceof", "new", "throw", "try",
      "catch", "finally", "class", "interface", "extends", "implements", "package", "import"
    )
  }
}