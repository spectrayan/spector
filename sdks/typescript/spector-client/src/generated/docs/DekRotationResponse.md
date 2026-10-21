
# DekRotationResponse


## Properties

Name | Type
------------ | -------------
`tenantId` | string
`rotatedNamespaceCount` | number
`namespaces` | [Array&lt;DekRotationItem&gt;](DekRotationItem.md)
`rotatedAt` | Date

## Example

```typescript
import type { DekRotationResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "tenantId": null,
  "rotatedNamespaceCount": null,
  "namespaces": null,
  "rotatedAt": null,
} satisfies DekRotationResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as DekRotationResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


