-- Redis time coordinates callers on different services. Replicate effects on Redis 6 as well.
redis.replicate_commands()
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + tonumber(time[2]) / 1000
local tokens, stamps, rates, capacities = {}, {}, {}, {}
local retry, blocked = 0, 0
for i = 1, 2 do
  rates[i] = tonumber(ARGV[2*i-1])
  capacities[i] = tonumber(ARGV[2*i])
  local previous = redis.call('HMGET', KEYS[i], 'tokens', 'time')
  stamps[i] = math.max(now, tonumber(previous[2]) or now)
  tokens[i] = math.min(capacities[i], (tonumber(previous[1]) or capacities[i])
      + math.max(0, now - (tonumber(previous[2]) or now)) * rates[i] / 60000)
  if tokens[i] < 1 then
    local wait = math.ceil((1 - tokens[i]) * 60 / rates[i])
    if wait > retry then retry, blocked = wait, i end
  end
end
local allowed = retry == 0
for i = 1, 2 do
  if allowed then tokens[i] = tokens[i] - 1 end
  redis.call('HSET', KEYS[i], 'tokens', tostring(tokens[i]), 'time', tostring(stamps[i]))
  -- An idle bucket has fully refilled before it expires. No permanent per-user records.
  redis.call('EXPIRE', KEYS[i], math.ceil(capacities[i] * 60 / rates[i]) + 1)
end
return {allowed and 1 or 0, retry, blocked}
