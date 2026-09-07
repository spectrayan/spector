
# ProviderHealth


## Properties

Name | Type
------------ | -------------
`name` | string
`status` | string
`latency` | string
`message` | string
`checkedAt` | Date
`healthy` | boolean

## Example

```typescript
import type { ProviderHealth } from ''

// TODO: Update the object below with actual values
const example = {
  "name": null,
  "status": null,
  "latency": null,
  "message": null,
  "checkedAt": null,
  "healthy": null,
} satisfies ProviderHealth

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ProviderHealth
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


