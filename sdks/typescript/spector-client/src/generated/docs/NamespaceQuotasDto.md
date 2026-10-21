
# NamespaceQuotasDto


## Properties

Name | Type
------------ | -------------
`maxMemories` | number
`maxStorageBytes` | number
`maxOpsPerMinute` | number

## Example

```typescript
import type { NamespaceQuotasDto } from ''

// TODO: Update the object below with actual values
const example = {
  "maxMemories": null,
  "maxStorageBytes": null,
  "maxOpsPerMinute": null,
} satisfies NamespaceQuotasDto

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as NamespaceQuotasDto
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


