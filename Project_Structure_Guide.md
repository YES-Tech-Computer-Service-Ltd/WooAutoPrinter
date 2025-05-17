# WooAutoPrinter Project Structure Guide

## Purpose of the Project Structure Document

We have created a detailed project structure document (`PROJECT_STRUCTURE.md`) with the following main purposes:

1. **Help developers quickly understand the project architecture**: New developers can quickly understand the entire project structure and functionality of each module through this document.
2. **Avoid duplicate feature development**: By reviewing the feature list in the document, you can confirm whether a feature has already been implemented, avoiding redundant development.
3. **Guide correct usage of existing interfaces**: The document lists the main functions of each module and class, helping developers find and correctly use existing interfaces.
4. **Promote code consistency**: Understanding the existing architecture and patterns ensures new code follows the same architectural principles.

## How to Make Cursor Use This Document

Cursor has been configured to use this document as part of the project context. The specific configuration is in `.cursor/settings.json`:

```json
{
  "projectStructureFile": "PROJECT_STRUCTURE.md",
  "documentationPaths": [
    "PROJECT_STRUCTURE.md"
  ]
}
```

With this configuration, Cursor will reference this document when parsing and understanding code, thus:

1. Providing more accurate code completion suggestions
2. Offering better contextual understanding when searching for files and functions
3. Following the project's existing structure and naming conventions when generating code

## How to Maintain and Update the Project Structure Document

To ensure the document always reflects the latest project structure and functionality, we recommend:

1. **Update the document when adding new features**: Whenever you add new important classes, methods, or features, update the PROJECT_STRUCTURE.md file accordingly.
2. **Update the document when adjusting structure**: When making project structure adjustments or refactors, ensure you modify the corresponding document sections.
3. **Keep categorization clear**: Continue to follow the existing layered structure (presentation layer, domain layer, data layer, etc.) to organize document content.
4. **Standard format for adding new classes**:
   ```
   - `ClassName.kt` - Brief functional description
     - `methodName()` - Method function description
   ```

## Tips for Effectively Using the Project Structure Document

1. **Consult before developing new features**: Before starting to develop new features, consult the document to understand if similar functionality has already been implemented.
2. **Find existing implementation methods**: When implementing a function, use the document to find similar implementations to maintain consistency.
3. **Understand data flow**: Use the document to understand the application's data flow and processing logic, such as order processing workflows, printing processes, etc.
4. **Module dependencies**: Understand the dependencies between modules to avoid creating circular dependencies.

## Document Query Examples

1. **Finding how to process new orders**:
   - Check the "Order Processing Flow" in the "Feature Flow" section
   - Find the `processNewOrders()` method in `/service/BackgroundPollingService.kt`

2. **Finding printer connection related code**:
   - Look for corresponding printer connection implementation classes in the `/data/printer/` directory
   - Reference the interface definition in `/domain/printer/PrinterConnection.kt`

3. **Adding new settings item**:
   - Reference the settings component implementation in `/presentation/screens/SettingsScreen.kt`
   - Check if you need to add a corresponding storage mechanism in the data layer

## Conclusion

By maintaining and using this project structure document, we can:
- Improve development efficiency
- Reduce code duplication
- Maintain project architecture consistency
- Lower the learning curve for new feature development

Please have all team members jointly maintain this document to ensure it stays up-to-date as the project evolves. 