
# ReflectRequest


## Properties

Name | Type
------------ | -------------
`sweepId` | string
`sessionLimit` | number
`sessionIdAfter` | number
`from` | number
`to` | number
`consolidationOnly` | boolean

## Example

```typescript
import type { ReflectRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "sweepId": null,
  "sessionLimit": null,
  "sessionIdAfter": null,
  "from": null,
  "to": null,
  "consolidationOnly": null,
} satisfies ReflectRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ReflectRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


