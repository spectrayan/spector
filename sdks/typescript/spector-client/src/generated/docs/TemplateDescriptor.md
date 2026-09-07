
# TemplateDescriptor


## Properties

Name | Type
------------ | -------------
`templateId` | string
`displayName` | string
`description` | string
`icon` | string
`category` | string
`connectorType` | string
`parameters` | [Array&lt;ParameterDefinition&gt;](ParameterDefinition.md)
`requiresCredential` | boolean
`builtIn` | boolean
`routeYaml` | string
`createdAt` | Date

## Example

```typescript
import type { TemplateDescriptor } from ''

// TODO: Update the object below with actual values
const example = {
  "templateId": null,
  "displayName": null,
  "description": null,
  "icon": null,
  "category": null,
  "connectorType": null,
  "parameters": null,
  "requiresCredential": null,
  "builtIn": null,
  "routeYaml": null,
  "createdAt": null,
} satisfies TemplateDescriptor

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TemplateDescriptor
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


