
# UpdateNamespaceRequest


## Properties

Name | Type
------------ | -------------
`displayName` | string
`description` | string
`type` | string
`bias` | [NamespaceBias](NamespaceBias.md)

## Example

```typescript
import type { UpdateNamespaceRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "displayName": null,
  "description": null,
  "type": null,
  "bias": null,
} satisfies UpdateNamespaceRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as UpdateNamespaceRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


