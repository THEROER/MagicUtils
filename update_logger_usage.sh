#!/bin/bash

# Add imports for LoggerGen and PrefixedLoggerGen after existing logger imports
find src -name "*.java" -exec sed -i '/import dev\.ua\.theroer\.magicutils\.logger\.PrefixedLogger;/a\
import dev.ua.theroer.magicutils.logger.PrefixedLoggerGen;\
import dev.ua.theroer.magicutils.logger.LoggerGen;' {} \;

# Replace PrefixedLogger method calls
find src -name "*.java" -exec sed -i -E 's/logger\.debug\((.+)\)/PrefixedLoggerGen.debug(logger, \1)/g' {} \;
find src -name "*.java" -exec sed -i -E 's/logger\.info\((.+)\)/PrefixedLoggerGen.info(logger, \1)/g' {} \;
find src -name "*.java" -exec sed -i -E 's/logger\.error\((.+)\)/PrefixedLoggerGen.error(logger, \1)/g' {} \;
find src -name "*.java" -exec sed -i -E 's/logger\.warn\((.+)\)/PrefixedLoggerGen.warn(logger, \1)/g' {} \;
find src -name "*.java" -exec sed -i -E 's/logger\.success\((.+)\)/PrefixedLoggerGen.success(logger, \1)/g' {} \;

# Replace static Logger method calls
find src -name "*.java" -exec sed -i -E 's/Logger\.debug\((.+)\)/LoggerGen.debug(\1)/g' {} \;
find src -name "*.java" -exec sed -i -E 's/Logger\.info\(([^)]+)\)/LoggerGen.info(\1)/g' {} \;
find src -name "*.java" -exec sed -i -E 's/Logger\.error\(([^)]+)\)/LoggerGen.error(\1)/g' {} \;
find src -name "*.java" -exec sed -i -E 's/Logger\.warn\((.+)\)/LoggerGen.warn(\1)/g' {} \;
find src -name "*.java" -exec sed -i -E 's/Logger\.success\((.+)\)/LoggerGen.success(\1)/g' {} \;

echo "Logger usage updated!"