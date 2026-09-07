
# TaskStatus


## Properties

Name | Type
------------ | -------------
`id` | string
`namespaceId` | string
`state` | string
`previousFireTime` | Date
`nextFireTime` | Date
`description` | string
`metadata` | { [key: string]: any | null; }

## Example

```typescript
import type { TaskStatus } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "namespaceId": null,
  "state": null,
  "previousFireTime": null,
  "nextFireTime": null,
  "description": null,
  "metadata": null,
} satisfies TaskStatus

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TaskStatus
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


