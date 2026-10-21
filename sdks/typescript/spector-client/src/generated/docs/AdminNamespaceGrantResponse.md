
# AdminNamespaceGrantResponse


## Properties

Name | Type
------------ | -------------
`grantId` | string
`namespaceId` | string
`principalId` | string
`principalUsername` | string
`principalType` | string
`role` | string
`actions` | Array&lt;string&gt;
`grantedBy` | string
`grantedAt` | Date
`expiresAt` | Date

## Example

```typescript
import type { AdminNamespaceGrantResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "grantId": null,
  "namespaceId": null,
  "principalId": null,
  "principalUsername": null,
  "principalType": null,
  "role": null,
  "actions": null,
  "grantedBy": null,
  "grantedAt": null,
  "expiresAt": null,
} satisfies AdminNamespaceGrantResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AdminNamespaceGrantResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


