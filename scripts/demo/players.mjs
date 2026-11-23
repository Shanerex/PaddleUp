// Demo season data for the cricket-t20 pack: 72 players in seven sets, prices in lakh (the pack's
// smallest unit). Tier drives how hard the bots chase a player (1 = star, 3 = squad filler).
// [name, country, role, tags, basePrice, set, tier]
const P = [
  // Marquee
  ["Virat Kohli", "India", "BAT", ["capped"], 200, "Marquee", 1],
  ["Jasprit Bumrah", "India", "BOWL", ["capped"], 200, "Marquee", 1],
  ["Rashid Khan", "Afghanistan", "BOWL", ["capped", "overseas"], 200, "Marquee", 1],
  ["Jos Buttler", "England", "WK", ["capped", "overseas"], 200, "Marquee", 1],
  ["Hardik Pandya", "India", "AR", ["capped"], 200, "Marquee", 1],
  ["Pat Cummins", "Australia", "BOWL", ["capped", "overseas"], 200, "Marquee", 1],
  // Batters
  ["Shubman Gill", "India", "BAT", ["capped"], 150, "Batters", 1],
  ["Suryakumar Yadav", "India", "BAT", ["capped"], 150, "Batters", 1],
  ["Travis Head", "Australia", "BAT", ["capped", "overseas"], 150, "Batters", 1],
  ["Yashasvi Jaiswal", "India", "BAT", ["capped"], 100, "Batters", 2],
  ["Ruturaj Gaikwad", "India", "BAT", ["capped"], 100, "Batters", 2],
  ["David Miller", "South Africa", "BAT", ["capped", "overseas"], 100, "Batters", 2],
  ["Faf du Plessis", "South Africa", "BAT", ["capped", "overseas"], 75, "Batters", 2],
  ["Shreyas Iyer", "India", "BAT", ["capped"], 100, "Batters", 2],
  ["Rinku Singh", "India", "BAT", ["capped"], 75, "Batters", 2],
  ["Tilak Varma", "India", "BAT", ["capped"], 75, "Batters", 2],
  ["Harry Brook", "England", "BAT", ["capped", "overseas"], 75, "Batters", 3],
  ["Devdutt Padikkal", "India", "BAT", ["capped"], 50, "Batters", 3],
  // Wicket-keepers
  ["Rishabh Pant", "India", "WK", ["capped"], 200, "Wicket-keepers", 1],
  ["KL Rahul", "India", "WK", ["capped"], 150, "Wicket-keepers", 1],
  ["Sanju Samson", "India", "WK", ["capped"], 100, "Wicket-keepers", 2],
  ["Heinrich Klaasen", "South Africa", "WK", ["capped", "overseas"], 150, "Wicket-keepers", 1],
  ["Phil Salt", "England", "WK", ["capped", "overseas"], 100, "Wicket-keepers", 2],
  ["Ishan Kishan", "India", "WK", ["capped"], 75, "Wicket-keepers", 2],
  ["Quinton de Kock", "South Africa", "WK", ["capped", "overseas"], 75, "Wicket-keepers", 3],
  ["Dhruv Jurel", "India", "WK", ["capped"], 50, "Wicket-keepers", 3],
  ["Jitesh Sharma", "India", "WK", ["capped"], 30, "Wicket-keepers", 3],
  // All-rounders
  ["Ravindra Jadeja", "India", "AR", ["capped"], 150, "All-rounders", 1],
  ["Andre Russell", "West Indies", "AR", ["capped", "overseas"], 150, "All-rounders", 1],
  ["Glenn Maxwell", "Australia", "AR", ["capped", "overseas"], 100, "All-rounders", 2],
  ["Axar Patel", "India", "AR", ["capped"], 100, "All-rounders", 2],
  ["Sunil Narine", "West Indies", "AR", ["capped", "overseas"], 100, "All-rounders", 2],
  ["Liam Livingstone", "England", "AR", ["capped", "overseas"], 75, "All-rounders", 2],
  ["Marcus Stoinis", "Australia", "AR", ["capped", "overseas"], 75, "All-rounders", 3],
  ["Washington Sundar", "India", "AR", ["capped"], 75, "All-rounders", 3],
  ["Shivam Dube", "India", "AR", ["capped"], 75, "All-rounders", 2],
  ["Krunal Pandya", "India", "AR", ["capped"], 50, "All-rounders", 3],
  ["Sam Curran", "England", "AR", ["capped", "overseas"], 100, "All-rounders", 2],
  ["Nitish Kumar Reddy", "India", "AR", ["capped"], 50, "All-rounders", 3],
  // Fast bowlers
  ["Mohammed Shami", "India", "BOWL", ["capped"], 150, "Fast bowlers", 1],
  ["Mohammed Siraj", "India", "BOWL", ["capped"], 100, "Fast bowlers", 2],
  ["Trent Boult", "New Zealand", "BOWL", ["capped", "overseas"], 100, "Fast bowlers", 2],
  ["Kagiso Rabada", "South Africa", "BOWL", ["capped", "overseas"], 100, "Fast bowlers", 2],
  ["Arshdeep Singh", "India", "BOWL", ["capped"], 100, "Fast bowlers", 2],
  ["Mitchell Starc", "Australia", "BOWL", ["capped", "overseas"], 150, "Fast bowlers", 1],
  ["Bhuvneshwar Kumar", "India", "BOWL", ["capped"], 75, "Fast bowlers", 3],
  ["Harshal Patel", "India", "BOWL", ["capped"], 75, "Fast bowlers", 3],
  ["Avesh Khan", "India", "BOWL", ["capped"], 50, "Fast bowlers", 3],
  ["Mark Wood", "England", "BOWL", ["capped", "overseas"], 75, "Fast bowlers", 3],
  ["T Natarajan", "India", "BOWL", ["capped"], 50, "Fast bowlers", 3],
  // Spinners
  ["Yuzvendra Chahal", "India", "BOWL", ["capped"], 100, "Spinners", 2],
  ["Kuldeep Yadav", "India", "BOWL", ["capped"], 100, "Spinners", 2],
  ["Varun Chakravarthy", "India", "BOWL", ["capped"], 75, "Spinners", 2],
  ["Ravi Bishnoi", "India", "BOWL", ["capped"], 75, "Spinners", 3],
  ["Adam Zampa", "Australia", "BOWL", ["capped", "overseas"], 50, "Spinners", 3],
  ["Wanindu Hasaranga", "Sri Lanka", "AR", ["capped", "overseas"], 100, "Spinners", 2],
  ["Noor Ahmad", "Afghanistan", "BOWL", ["capped", "overseas"], 50, "Spinners", 3],
  ["R Sai Kishore", "India", "BOWL", ["capped"], 30, "Spinners", 3],
  // Uncapped
  ["Abhishek Sharma", "India", "BAT", ["uncapped"], 30, "Uncapped", 2],
  ["Riyan Parag", "India", "AR", ["uncapped"], 30, "Uncapped", 2],
  ["Ayush Badoni", "India", "BAT", ["uncapped"], 20, "Uncapped", 3],
  ["Mayank Yadav", "India", "BOWL", ["uncapped"], 30, "Uncapped", 2],
  ["Shashank Singh", "India", "BAT", ["uncapped"], 20, "Uncapped", 3],
  ["Prabhsimran Singh", "India", "WK", ["uncapped"], 20, "Uncapped", 3],
  ["Harshit Rana", "India", "BOWL", ["uncapped"], 30, "Uncapped", 3],
  ["Ashutosh Sharma", "India", "BAT", ["uncapped"], 20, "Uncapped", 3],
  ["Ramandeep Singh", "India", "AR", ["uncapped"], 20, "Uncapped", 3],
  ["Vaibhav Arora", "India", "BOWL", ["uncapped"], 20, "Uncapped", 3],
  ["Naman Dhir", "India", "AR", ["uncapped"], 20, "Uncapped", 3],
  ["Abishek Porel", "India", "WK", ["uncapped"], 20, "Uncapped", 3],
  ["Suyash Sharma", "India", "BOWL", ["uncapped"], 20, "Uncapped", 3],
];

export const PLAYERS = P.map(([name, country, role, tags, basePrice, set, tier], i) => ({
  name, country, role, tags, basePrice, set, tier, providerId: `demo:${i + 1}`,
}));

export const TIER_BY_NAME = new Map(PLAYERS.map((p) => [p.name, p.tier]));

export function seasonCsv() {
  const rows = PLAYERS.map((p) => [p.name, p.country, p.role, p.tags.join(";"), p.basePrice, p.set, p.providerId].join(","));
  return "name,country,role,tags,base_price,set,provider_id\n" + rows.join("\n") + "\n";
}
