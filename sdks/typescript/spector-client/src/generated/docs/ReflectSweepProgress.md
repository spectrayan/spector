
# ReflectSweepProgress


## Properties

Name | Type
------------ | -------------
`sweepId` | string
`sessionsCompleted` | number
`factsIngested` | number
`turnsMarked` | number
`backlogRemaining` | number
`status` | string
`startedAt` | Date
`updatedAt` | Date

## Example

```typescript
import type { ReflectSweepProgress } from ''

// TODO: Update the object below with actual values
const example = {
  "sweepId": null,
  "sessionsCompleted": null,
  "factsIngested": null,
  "turnsMarked": null,
  "backlogRemaining": null,
  "status": null,
  "startedAt": null,
  "updatedAt": null,
} satisfies ReflectSweepProgress

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ReflectSweepProgress
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


