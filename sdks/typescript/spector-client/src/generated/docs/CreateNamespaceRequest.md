
# CreateNamespaceRequest


## Properties

Name | Type
------------ | -------------
`slug` | string
`type` | string
`displayName` | string
`description` | string
`bias` | [NamespaceBias](NamespaceBias.md)

## Example

```typescript
import type { CreateNamespaceRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "slug": null,
  "type": null,
  "displayName": null,
  "description": null,
  "bias": null,
} satisfies CreateNamespaceRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CreateNamespaceRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


